package com.kovospace.newtablinks.entitlement.services;

import com.kovospace.newtablinks.entitlement.models.EntitlementEntity;
import com.kovospace.newtablinks.entitlement.models.ProStanding;
import com.kovospace.newtablinks.entitlement.repositories.EntitlementRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Answers, for other modules, where an account stands with pro - read only.
 *
 * <p>The reading counterpart of {@link EntitlementSignalService}, and deliberately separate from
 * it: this depends on nothing but the repository, so the user module can ask "is this account
 * pro" without a dependency cycle through the service that resolves accounts for payments.</p>
 *
 * @since 0.0.9
 */
@Service
public class EntitlementStandingService {

    private final EntitlementRepository entitlementRepository;

    /**
     * Creates the service.
     *
     * @param entitlementRepository persistence access for entitlements
     */
    public EntitlementStandingService(final EntitlementRepository entitlementRepository) {
        this.entitlementRepository = entitlementRepository;
    }

    /**
     * Tells whether an account is pro right now.
     *
     * @param ownerId identifier of the account
     * @return {@code true} while its entitlement grants pro; {@code false} when it has none
     */
    @Transactional(readOnly = true)
    public boolean isAccountProNow(final UUID ownerId) {
        return isAccountProAt(ownerId, Instant.now());
    }

    /**
     * Tells whether an account is pro at the given moment.
     *
     * @param ownerId identifier of the account
     * @param moment  the instant to judge at
     * @return {@code true} while its entitlement grants pro; {@code false} when it has none
     */
    @Transactional(readOnly = true)
    public boolean isAccountProAt(final UUID ownerId, final Instant moment) {
        return entitlementRepository.findByOwnerId(ownerId)
                .map(entitlement -> entitlement.grantsProAt(moment))
                .orElse(false);
    }

    /**
     * Tells whether each of several accounts is pro right now, and through what - in one query.
     *
     * @param ownerIds identifiers of the accounts
     * @return a standing for every identifier given, {@link ProStanding#NOT_PRO} for an account
     *         without an entitlement; empty when no identifier was given
     */
    @Transactional(readOnly = true)
    public Map<UUID, ProStanding> describeProStandingNowOfEach(final Collection<UUID> ownerIds) {
        if (ownerIds.isEmpty()) {
            return Map.of();
        }
        final Instant now = Instant.now();
        final Map<UUID, ProStanding> standingByOwnerId = new HashMap<>();
        ownerIds.forEach(ownerId -> standingByOwnerId.put(ownerId, ProStanding.NOT_PRO));
        entitlementRepository.findAllByOwnerIdIn(ownerIds).forEach(entitlement ->
                standingByOwnerId.put(
                        entitlement.getOwner().getId(), ProStanding.of(entitlement, now)));
        return Map.copyOf(standingByOwnerId);
    }

    /**
     * Tells whether an account is pro right now, and through what.
     *
     * @param ownerId identifier of the account
     * @return its standing; {@link ProStanding#NOT_PRO} when it has no entitlement
     */
    @Transactional(readOnly = true)
    public ProStanding describeProStandingNow(final UUID ownerId) {
        final Instant now = Instant.now();
        return entitlementRepository.findByOwnerId(ownerId)
                .map(entitlement -> ProStanding.of(entitlement, now))
                .orElse(ProStanding.NOT_PRO);
    }

    /**
     * Returns an account's entitlement, without locking it.
     *
     * <p>The only way the entity leaves this module; callers read it inside their own
     * transaction and map it before it reaches a controller.</p>
     *
     * @param ownerId identifier of the account
     * @return the entitlement, or empty when the account never had one
     */
    @Transactional(readOnly = true)
    public Optional<EntitlementEntity> findEntitlementEntity(final UUID ownerId) {
        return entitlementRepository.findByOwnerId(ownerId);
    }
}
