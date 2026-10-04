package com.kovospace.newtablinks.entitlement.services;

import com.kovospace.newtablinks.common.exceptions.PaidEntitlementRevocationException;
import com.kovospace.newtablinks.entitlement.models.EntitlementEntity;
import com.kovospace.newtablinks.entitlement.models.OperatorProDecisionOutcome;
import com.kovospace.newtablinks.entitlement.models.PremiumGrantTerm;
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
 *   <li><strong>A paid entitlement is never touched</strong> by a grant, whatever term is named;
 *       it is never downgraded to a grant.</li>
 *   <li><strong>Granting an account already pro through a grant, without naming a term</strong>,
 *       does nothing. The admin form sends {@code premium} on every save, so this is what keeps
 *       an unrelated edit from silently pushing a one-year grant's end further out.</li>
 *   <li><strong>Granting an account already pro through a grant, naming a term</strong>,
 *       re-applies the grant with that term counted from now: the operator switches between one
 *       year and lifetime, or renews a year.</li>
 *   <li><strong>Granting an account that is not pro</strong> writes a grant of the named term,
 *       lifetime when none is named - into a new row, or over a lapsed one in place, since an
 *       account has one row at most.</li>
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
     * Makes an account pro through an operator grant of the given term, following the rules in
     * the class description.
     *
     * <p>Joins the caller's transaction and locks the account's entitlement row until it ends.</p>
     *
     * @param owner     the account, already persisted
     * @param grantTerm how long the grant lasts; {@code null} leaves an existing grant as it is
     *                  and makes a new one {@link PremiumGrantTerm#DEFAULT_TERM}
     * @return {@link OperatorProDecisionOutcome#GRANTED} for a new grant,
     *         {@link OperatorProDecisionOutcome#GRANT_TERM_REAPPLIED} for an existing grant given
     *         a new term, or {@link OperatorProDecisionOutcome#ALREADY_PRO} when nothing was
     *         changed
     * @since 0.0.15
     */
    @Transactional
    public OperatorProDecisionOutcome grantPro(
            final UserEntity owner,
            final PremiumGrantTerm grantTerm) {

        final Instant now = Instant.now();
        final Optional<EntitlementEntity> existing =
                entitlementRepository.findByOwnerIdForUpdate(owner.getId());
        if (existing.isPresent() && existing.get().grantsProAt(now)) {
            return reapplyStandingGrantWhenTermNamed(existing.get(), grantTerm, now);
        }

        final EntitlementEntity entitlement = existing.orElseGet(() -> new EntitlementEntity(owner));
        final PremiumGrantTerm effectiveTerm =
                grantTerm == null ? PremiumGrantTerm.DEFAULT_TERM : grantTerm;
        entitlement.becomeOperatorGrant(effectiveTerm.grantedUntilWhenGrantedAt(now));
        entitlementRepository.save(entitlement);
        return OperatorProDecisionOutcome.GRANTED;
    }

    /**
     * Gives a standing grant the named term from now; leaves a purchase, or a grant when no term
     * is named, exactly as it is.
     *
     * @param entitlement the locked entitlement, which grants pro at {@code now}
     * @param grantTerm   the term the operator named, or {@code null}
     * @param now         the moment the term is counted from
     * @return {@link OperatorProDecisionOutcome#GRANT_TERM_REAPPLIED} or
     *         {@link OperatorProDecisionOutcome#ALREADY_PRO}
     */
    private OperatorProDecisionOutcome reapplyStandingGrantWhenTermNamed(
            final EntitlementEntity entitlement,
            final PremiumGrantTerm grantTerm,
            final Instant now) {

        if (grantTerm == null || !entitlement.isOperatorGrant()) {
            return OperatorProDecisionOutcome.ALREADY_PRO;
        }
        entitlement.becomeOperatorGrant(grantTerm.grantedUntilWhenGrantedAt(now));
        entitlementRepository.save(entitlement);
        return OperatorProDecisionOutcome.GRANT_TERM_REAPPLIED;
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
