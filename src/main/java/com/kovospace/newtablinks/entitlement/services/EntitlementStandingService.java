package com.kovospace.newtablinks.entitlement.services;

import com.kovospace.newtablinks.entitlement.models.EntitlementEntity;
import com.kovospace.newtablinks.entitlement.repositories.EntitlementRepository;
import java.time.Instant;
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
