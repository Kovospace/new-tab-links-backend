package com.kovospace.newtablinks.entitlement.repositories;

import com.kovospace.newtablinks.entitlement.models.EntitlementEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Persistence access for {@link EntitlementEntity}.
 *
 * @since 0.0.9
 */
@Repository
public interface EntitlementRepository extends JpaRepository<EntitlementEntity, UUID> {

    /**
     * Loads an account's entitlement and locks its row until the transaction ends.
     *
     * <p>The lock is what makes the out-of-order guard hold under concurrency: two webhook
     * deliveries for one account would otherwise both read the same "newest applied event",
     * both pass the check, and the later commit would win whatever its age.</p>
     *
     * @param ownerId identifier of the account
     * @return the entitlement, or empty when the account has none
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select entitlement from EntitlementEntity entitlement "
            + "where entitlement.owner.id = :ownerId")
    Optional<EntitlementEntity> findByOwnerIdForUpdate(@Param("ownerId") UUID ownerId);

    /**
     * Loads an account's entitlement without locking it.
     *
     * @param ownerId identifier of the account
     * @return the entitlement, or empty when the account has none
     */
    Optional<EntitlementEntity> findByOwnerId(UUID ownerId);

    /**
     * Finds the entitlement resting on a provider subscription.
     *
     * @param providerSubscriptionId the provider's subscription identifier
     * @return the entitlement, or empty when none rests on it
     */
    Optional<EntitlementEntity> findFirstByProviderSubscriptionId(String providerSubscriptionId);

    /**
     * Finds an entitlement paid for by a provider customer.
     *
     * @param providerCustomerId the provider's customer identifier
     * @return an entitlement, or empty when the customer has none
     */
    Optional<EntitlementEntity> findFirstByProviderCustomerId(String providerCustomerId);
}
