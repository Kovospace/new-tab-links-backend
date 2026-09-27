package com.kovospace.newtablinks.entitlement.services;

import com.kovospace.newtablinks.common.exceptions.PaidEntitlementRevocationException;
import com.kovospace.newtablinks.entitlement.models.EntitlementEntity;
import com.kovospace.newtablinks.entitlement.models.OperatorProDecisionOutcome;
import com.kovospace.newtablinks.entitlement.repositories.EntitlementRepository;
import com.kovospace.newtablinks.user.models.UserEntity;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gives pro to an account without payment, and takes back only what was given that way.
 *
 * <p>The operator's half of the entitlement, next to {@link EntitlementSignalService}, which is
 * the payment provider's. Both lock the account's row before deciding, so a grant and a webhook
 * for the same account are applied one after the other rather than over each other.</p>
 *
 * <p>The rules:</p>
 * <ul>
 *   <li><strong>Granting an account that is already pro does nothing</strong>, whatever it is pro
 *       through. A paid entitlement is never downgraded to a grant.</li>
 *   <li><strong>Granting an account that is not pro</strong> writes a grant - into a new row, or
 *       over a lapsed one in place, since an account has one row at most.</li>
 *   <li><strong>Revoking a paid entitlement is refused.</strong> That is a refund or a
 *       cancellation at the provider, not something to switch off here.</li>
 *   <li><strong>Revoking a grant</strong> deletes the row, so the account reads as never having
 *       had a plan - unless the row still carries provider history, in which case it is kept
 *       and marked expired instead (see {@link EntitlementEntity#carriesProviderHistory()}).</li>
 * </ul>
 *
 * <p>A payment arriving later takes over a grant on its own: the transition policy treats a
 * confirmed payment as replacing whatever the row rested on. Nothing here has to arrange that.</p>
 *
 * <p>Logs nothing itself; the caller knows which operator action this was and logs the returned
 * outcome.</p>
 *
 * @since 0.0.10
 */
@Service
public class EntitlementGrantService {

    /** What the operator is told when trying to revoke something that was paid for. */
    static final String PAID_ENTITLEMENT_MESSAGE = "This account is premium through a payment. "
            + "A paid entitlement ends through a refund or a cancellation at the payment "
            + "provider, not by the operator.";

    private final EntitlementRepository entitlementRepository;

    /**
     * Creates the service.
     *
     * @param entitlementRepository persistence access for entitlements
     */
    public EntitlementGrantService(final EntitlementRepository entitlementRepository) {
        this.entitlementRepository = entitlementRepository;
    }

    /**
     * Makes an account pro through an operator grant, unless it is pro already.
     *
     * <p>Joins the caller's transaction and locks the account's entitlement row until it ends.</p>
     *
     * @param owner the account, already persisted
     * @return {@link OperatorProDecisionOutcome#GRANTED}, or
     *         {@link OperatorProDecisionOutcome#ALREADY_PRO} when nothing was changed
     */
    @Transactional
    public OperatorProDecisionOutcome grantProUnlessAlreadyPro(final UserEntity owner) {
        final Optional<EntitlementEntity> existing =
                entitlementRepository.findByOwnerIdForUpdate(owner.getId());
        if (existing.isPresent() && existing.get().grantsProAt(Instant.now())) {
            return OperatorProDecisionOutcome.ALREADY_PRO;
        }

        final EntitlementEntity entitlement = existing.orElseGet(() -> new EntitlementEntity(owner));
        entitlement.becomeOperatorGrant();
        entitlementRepository.save(entitlement);
        return OperatorProDecisionOutcome.GRANTED;
    }

    /**
     * Takes an operator grant away from an account.
     *
     * <p>Joins the caller's transaction and locks the account's entitlement row until it ends.
     * An account that is not pro is left as it is - there is nothing to take away.</p>
     *
     * @param owner the account
     * @return {@link OperatorProDecisionOutcome#REVOKED}, or
     *         {@link OperatorProDecisionOutcome#NOT_PRO} when nothing was changed
     * @throws PaidEntitlementRevocationException when the account is pro through a payment
     */
    @Transactional
    public OperatorProDecisionOutcome revokeGrantedPro(final UserEntity owner) {
        final Optional<EntitlementEntity> existing =
                entitlementRepository.findByOwnerIdForUpdate(owner.getId());
        if (existing.isEmpty() || !existing.get().grantsProAt(Instant.now())) {
            return OperatorProDecisionOutcome.NOT_PRO;
        }

        final EntitlementEntity entitlement = existing.get();
        if (!entitlement.isOperatorGrant()) {
            throw new PaidEntitlementRevocationException(PAID_ENTITLEMENT_MESSAGE);
        }
        removeOperatorGrant(entitlement);
        return OperatorProDecisionOutcome.REVOKED;
    }

    /**
     * Removes a standing grant: deletes the row, or ends it in place when deleting would lose
     * provider history.
     *
     * @param entitlement the locked grant
     */
    private void removeOperatorGrant(final EntitlementEntity entitlement) {
        if (entitlement.carriesProviderHistory()) {
            entitlement.endOperatorGrant();
            return;
        }
        entitlementRepository.delete(entitlement);
    }
}
