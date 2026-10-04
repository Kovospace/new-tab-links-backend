package com.kovospace.newtablinks.payment.mappers;

import com.kovospace.newtablinks.entitlement.models.EntitlementEntity;
import com.kovospace.newtablinks.entitlement.models.EntitlementSource;
import com.kovospace.newtablinks.entitlement.models.EntitlementStatus;
import com.kovospace.newtablinks.payment.dtos.SubscriptionStatusDto;
import com.kovospace.newtablinks.payment.models.SubscriptionPlan;
import com.kovospace.newtablinks.payment.models.SubscriptionState;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * Converts an account's entitlement into the plan and state the website shows.
 *
 * <p>Hand-written rather than MapStruct, because nearly every field is a decision rather than a
 * copy. One-directional, like every mapper here.</p>
 *
 * <p>Three things are fixed for now and documented so they are not mistaken for oversights:</p>
 * <ul>
 *   <li>{@code cancellable} and {@code refundable} are always {@code false}, and
 *       {@code refundableUntil} always {@code null}: no cancel or refund endpoint exists yet, and
 *       the website binds these flags directly, so {@code true} would offer a button that fails.</li>
 *   <li>{@code cancelledAt} is always {@code null}: the entitlement does not record when a
 *       cancellation happened.</li>
 *   <li>{@code pendingCheckout} is always {@code null}: nothing tracks a checkout before its
 *       webhook arrives.</li>
 * </ul>
 *
 * @since 0.0.9
 */
@Component
public class SubscriptionStatusMapper {

    /**
     * Describes an account that has never held a plan.
     *
     * @return plan {@code null}, state {@code NONE}, every date {@code null}, every flag false
     */
    public SubscriptionStatusDto toDtoForAccountWithoutEntitlement() {
        return new SubscriptionStatusDto(
                null, SubscriptionState.NONE, null, null, null, null, false, false, null, null,
                false);
    }

    /**
     * Describes an account's entitlement.
     *
     * @param entitlement the account's entitlement row
     * @return its plan, state and dates
     */
    public SubscriptionStatusDto toDto(final EntitlementEntity entitlement) {
        return new SubscriptionStatusDto(
                toPlan(entitlement),
                toState(entitlement.getStatus()),
                entitlement.getCreatedAt(),
                entitlement.getPaidUntil(),
                resolveRenewsAt(entitlement),
                null,
                false,
                false,
                null,
                null,
                entitlement.isOperatorGrant());
    }

    /**
     * Names the plan an entitlement stands for.
     *
     * <p>A grant is pro given by the operator, not a plan anyone bought, but the website shows
     * it the way it shows the plan it resembles: a grant without an end as {@code LIFETIME}, a
     * one-year grant as {@code YEARLY_RECURRING}. {@code grantedByOperator} on the same DTO is
     * what tells the two apart from a purchase, and {@code renewsAt} stays {@code null}, because
     * a grant renews only when the operator renews it.</p>
     *
     * @param entitlement the account's entitlement row
     * @return the plan
     */
    static SubscriptionPlan toPlan(final EntitlementEntity entitlement) {
        return switch (entitlement.getSource()) {
            case SUBSCRIPTION -> SubscriptionPlan.YEARLY_RECURRING;
            case LIFETIME -> SubscriptionPlan.LIFETIME;
            case GRANT -> entitlement.getPaidUntil() == null
                    ? SubscriptionPlan.LIFETIME
                    : SubscriptionPlan.YEARLY_RECURRING;
        };
    }

    /**
     * Names the state the website shows for a stored lifecycle status.
     *
     * @param status where the entitlement stands in the provider's lifecycle
     * @return the state. A scheduled cancellation reads as {@code CANCELLED}, which the website
     *         shows as paid up until the period end; a subscription that has ended reads as
     *         {@code EXPIRED}, the website's only state for one with nothing left to run
     */
    static SubscriptionState toState(final EntitlementStatus status) {
        return switch (status) {
            case ACTIVE -> SubscriptionState.ACTIVE;
            case PAST_DUE -> SubscriptionState.PAST_DUE;
            case SCHEDULED_CANCEL -> SubscriptionState.CANCELLED;
            case CANCELED, EXPIRED -> SubscriptionState.EXPIRED;
            case REFUNDED -> SubscriptionState.REFUNDED;
        };
    }

    /**
     * Tells when the next renewal is due.
     *
     * @param entitlement the account's entitlement row
     * @return the end of the paid period for an active subscription, otherwise {@code null}
     */
    private static Instant resolveRenewsAt(final EntitlementEntity entitlement) {
        final boolean renews = entitlement.getSource() == EntitlementSource.SUBSCRIPTION
                && entitlement.getStatus() == EntitlementStatus.ACTIVE;
        return renews ? entitlement.getPaidUntil() : null;
    }
}
