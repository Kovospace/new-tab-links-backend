package com.kovospace.newtablinks.payment.dtos;

import com.kovospace.newtablinks.payment.models.SubscriptionPlan;
import com.kovospace.newtablinks.payment.models.SubscriptionState;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * The signed-in account's pro plan and where it stands, for the website's account page.
 *
 * <p>Every field is always present in the JSON, {@code null} included; the website binds them
 * directly. {@code cancellable} and {@code refundable} are always {@code false} and
 * {@code refundableUntil} and {@code pendingCheckout} always {@code null} for now, because no
 * cancel or refund endpoint exists yet and no checkout is tracked before its webhook arrives.</p>
 *
 * @param plan            the plan held, or {@code null} for none; an operator grant reads as
 *                        {@code LIFETIME} without an end and {@code YEARLY_RECURRING} with one
 * @param state           where the plan stands; {@code NONE} when there has never been one
 * @param startedAt       when the account first became entitled, or {@code null}
 * @param validUntil      end of what has been paid for; {@code null} for lifetime or none
 * @param renewsAt        when the next renewal is due, only for an active subscription
 * @param cancelledAt     when the plan was cancelled; not recorded yet, so always {@code null}
 * @param cancellable     whether the website may offer cancelling; always {@code false} for now
 * @param refundable      whether the website may offer a refund; always {@code false} for now
 * @param refundableUntil until when a refund may be asked for; always {@code null} for now
 * @param pendingCheckout a checkout started but not yet confirmed; always {@code null} for now
 * @param grantedByOperator whether the plan was given by the operator rather than bought; since
 *                        0.0.15
 * @since 0.0.9
 */
@Schema(description = "The signed-in account's pro plan and where it stands")
public record SubscriptionStatusDto(

        @Schema(description = "The plan held; null when the account holds none. Pro granted by "
                + "the operator reads as LIFETIME when it has no end and YEARLY_RECURRING when "
                + "it has one - grantedByOperator tells it apart from a purchase.",
                nullable = true, example = "YEARLY_RECURRING")
        SubscriptionPlan plan,

        @Schema(description = "Where the plan stands; NONE when there has never been one. Does "
                + "not say whether the account is pro - read premium on the account for that.",
                example = "ACTIVE")
        SubscriptionState state,

        @Schema(description = "When the account first became entitled", nullable = true,
                example = "2026-09-27T10:00:00Z")
        Instant startedAt,

        @Schema(description = "End of the period paid for, or of a one-year operator grant; "
                + "null for a lifetime plan or grant",
                nullable = true, example = "2027-09-27T10:00:00Z")
        Instant validUntil,

        @Schema(description = "When the next renewal is due; null unless a yearly plan is active",
                nullable = true, example = "2027-09-27T10:00:00Z")
        Instant renewsAt,

        @Schema(description = "When the plan was cancelled. Not recorded yet, so always null.",
                nullable = true)
        Instant cancelledAt,

        @Schema(description = "Whether the website may offer cancelling. Always false: no "
                + "cancel endpoint exists yet.", example = "false")
        boolean cancellable,

        @Schema(description = "Whether the website may offer a refund. Always false: no refund "
                + "endpoint exists yet.", example = "false")
        boolean refundable,

        @Schema(description = "Until when a refund may be asked for. Always null for now.",
                nullable = true)
        Instant refundableUntil,

        @Schema(description = "A checkout started but not yet confirmed by the provider. Always "
                + "null for now; reserved so the shape does not change when it is tracked.",
                nullable = true, types = {"object", "null"})
        Object pendingCheckout,

        @Schema(description = "Whether the operator gave this plan rather than it being bought. "
                + "A grant never renews by itself, so renewsAt is null; validUntil is its end, "
                + "null for a grant without one.", example = "false")
        boolean grantedByOperator) {
}
