package com.kovospace.newtablinks.entitlement.services;

import com.kovospace.newtablinks.entitlement.models.EntitlementEntity;
import com.kovospace.newtablinks.entitlement.models.EntitlementSignal;
import com.kovospace.newtablinks.entitlement.models.EntitlementSignalKind;
import com.kovospace.newtablinks.entitlement.models.EntitlementSignalOutcome;
import com.kovospace.newtablinks.entitlement.models.ProviderPurchaseReferences;
import com.kovospace.newtablinks.entitlement.models.SupersededSubscriptionCancellation;
import com.kovospace.newtablinks.entitlement.repositories.EntitlementRepository;
import com.kovospace.newtablinks.user.models.UserEntity;
import com.kovospace.newtablinks.user.services.UserService;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes what a payment provider reported into the account's entitlement.
 *
 * <p>The provider-neutral half of the payment port: it receives an {@link EntitlementSignal}
 * that an adapter has already verified and translated, finds the account it belongs to, and lets
 * {@link EntitlementTransitionPolicy} decide what changes. It knows nothing about webhooks,
 * signatures or event names.</p>
 *
 * <p>When a lifetime purchase replaces a live subscription, it publishes a
 * {@link SupersededSubscriptionCancellation} event. Whoever cancels the subscription at the
 * provider listens for it after commit; this service never calls the provider itself.</p>
 *
 * @since 0.0.9
 */
@Service
public class EntitlementSignalService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EntitlementSignalService.class);

    private final EntitlementRepository entitlementRepository;
    private final EntitlementTransitionPolicy entitlementTransitionPolicy;
    private final UserService userService;
    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     * Creates the service.
     *
     * @param entitlementRepository       persistence access for entitlements
     * @param entitlementTransitionPolicy decides what a signal does to an entitlement
     * @param userService                 resolves the account a payment was made for
     * @param applicationEventPublisher   announces a subscription that must be cancelled
     */
    public EntitlementSignalService(
            final EntitlementRepository entitlementRepository,
            final EntitlementTransitionPolicy entitlementTransitionPolicy,
            final UserService userService,
            final ApplicationEventPublisher applicationEventPublisher) {

        this.entitlementRepository = entitlementRepository;
        this.entitlementTransitionPolicy = entitlementTransitionPolicy;
        this.userService = userService;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    /**
     * Applies one signal to the entitlement of the account it belongs to.
     *
     * <p>Joins the caller's transaction, so that the caller can record the outcome atomically with
     * the change. The account's entitlement row is locked for the rest of that transaction. Two
     * signals starting a first entitlement for the same account at the same moment collide on
     * the unique constraint and one of them fails; the caller is expected to let the provider
     * redeliver it.</p>
     *
     * @param signal what the provider reported, already verified
     * @return what applying it did
     */
    @Transactional
    public EntitlementSignalOutcome applySignal(final EntitlementSignal signal) {
        final Optional<UUID> accountId = resolveAccountId(signal);
        if (accountId.isEmpty()) {
            LOGGER.error("A {} signal from {} names no known account (subscription {}, customer "
                            + "{}); nothing was changed and an operator has to attribute it",
                    signal.kind(), signal.provider(),
                    signal.references().subscriptionId(), signal.references().customerId());
            return EntitlementSignalOutcome.UNATTRIBUTED;
        }

        final EntitlementSignalOutcome outcome = entitlementRepository
                .findByOwnerIdForUpdate(accountId.get())
                .map(existing -> applyToExisting(existing, signal))
                .orElseGet(() -> startEntitlement(accountId.get(), signal));

        LOGGER.info("{} signal from {} for account {}: {}",
                signal.kind(), signal.provider(), accountId.get(), outcome);
        return outcome;
    }

    /**
     * Applies a signal to an entitlement already on file, and announces the cancellation a
     * lifetime purchase left pending.
     *
     * <p>The announcement is an ordinary application event, published inside the caller's
     * transaction; a listener bound to the commit acts on it only once the entitlement is
     * stored. A newly created row never needs one - it rested on no subscription.</p>
     *
     * @param entitlement the locked entitlement
     * @param signal      what the provider reported
     * @return what applying it did
     */
    private EntitlementSignalOutcome applyToExisting(
            final EntitlementEntity entitlement,
            final EntitlementSignal signal) {

        final EntitlementSignalOutcome outcome =
                entitlementTransitionPolicy.apply(entitlement, false, signal);
        if (outcome == EntitlementSignalOutcome.APPLIED
                && signal.kind() == EntitlementSignalKind.LIFETIME_PURCHASED) {
            entitlement.pendingSupersededSubscriptionId().ifPresent(subscriptionId -> {
                LOGGER.info("Lifetime purchase for account {} replaced subscription {}; it will "
                        + "be cancelled at {} once this commits",
                        entitlement.getOwner().getId(), subscriptionId, signal.provider());
                applicationEventPublisher.publishEvent(new SupersededSubscriptionCancellation(
                        entitlement.getId(), subscriptionId));
            });
        }
        return outcome;
    }

    /**
     * Creates the first entitlement of an account from a signal, when the signal may start one.
     *
     * @param accountId the account, known to exist
     * @param signal    what the provider reported
     * @return what applying it did
     */
    private EntitlementSignalOutcome startEntitlement(
            final UUID accountId,
            final EntitlementSignal signal) {

        if (!entitlementTransitionPolicy.mayStartEntitlement(signal)) {
            return EntitlementSignalOutcome.IGNORED_UNRELATED;
        }
        final Optional<UserEntity> owner = userService.findUserEntity(accountId);
        if (owner.isEmpty()) {
            return EntitlementSignalOutcome.UNATTRIBUTED;
        }

        final EntitlementEntity entitlement = new EntitlementEntity(owner.get());
        final EntitlementSignalOutcome outcome =
                entitlementTransitionPolicy.apply(entitlement, true, signal);
        entitlementRepository.saveAndFlush(entitlement);
        return outcome;
    }

    /**
     * Finds the account a signal belongs to.
     *
     * <p>The account named at checkout comes first: it is set by this service's own server-side
     * call, so it cannot be forged by the buyer, and it survives on every subscription event
     * because the provider copies checkout metadata onto the subscription. Only a signal that
     * carries none falls back to the purchase identifiers of an entitlement already on file.</p>
     *
     * @param signal what the provider reported
     * @return the account identifier, or empty when nobody can be found
     */
    private Optional<UUID> resolveAccountId(final EntitlementSignal signal) {
        if (signal.attributedAccountId() != null) {
            return userService.findUserEntity(signal.attributedAccountId())
                    .map(UserEntity::getId);
        }
        return findEntitlementByPurchase(signal.references())
                .map(entitlement -> entitlement.getOwner().getId());
    }

    /**
     * Finds an entitlement by the subscription it rests on, and failing that by its customer.
     *
     * @param references the provider's identifiers carried by the signal
     * @return the entitlement, or empty when none matches
     */
    private Optional<EntitlementEntity> findEntitlementByPurchase(
            final ProviderPurchaseReferences references) {

        if (references.subscriptionId() != null) {
            final Optional<EntitlementEntity> bySubscription =
                    entitlementRepository.findFirstByProviderSubscriptionId(
                            references.subscriptionId());
            if (bySubscription.isPresent()) {
                return bySubscription;
            }
        }
        if (references.customerId() != null) {
            return entitlementRepository.findFirstByProviderCustomerId(references.customerId());
        }
        return Optional.empty();
    }
}
