package com.kovospace.newtablinks.entitlement.repositories;

import com.kovospace.newtablinks.entitlement.models.EntitlementEntity;
import com.kovospace.newtablinks.entitlement.models.SupersededSubscriptionCancellation;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
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

    /**
     * Lists the superseded subscriptions still waiting to be cancelled at the provider, the
     * longest-waiting first.
     *
     * <p>Served by the partial index {@code ix_user_entitlement_superseded_pending}.</p>
     *
     * @param limit how many to return at most
     * @return the pending cancellations, possibly empty
     */
    @Query("select new com.kovospace.newtablinks.entitlement.models."
            + "SupersededSubscriptionCancellation(entitlement.id, "
            + "entitlement.supersededSubscriptionId) "
            + "from EntitlementEntity entitlement "
            + "where entitlement.supersededSubscriptionId is not null "
            + "and entitlement.supersededSubscriptionCancelledAt is null "
            + "order by entitlement.updatedAt")
    List<SupersededSubscriptionCancellation> findPendingSupersededSubscriptionCancellations(
            Limit limit);

    /**
     * Marks a superseded subscription cancelled, provided the row still waits on exactly that
     * subscription.
     *
     * <p>A single conditional statement rather than a read and a write, so that it needs no lock
     * and is safe to race: against another replica marking the same row (the second changes
     * nothing), and against a webhook that meanwhile scheduled a different subscription (the
     * condition no longer holds, and the newer one stays pending).</p>
     *
     * @param entitlementId  the row
     * @param subscriptionId the subscription the provider confirmed cancelled
     * @param cancelledAt    when that was confirmed
     * @return {@code 1} when the row was marked, {@code 0} when it no longer waited on it
     */
    @Modifying
    @Query("update EntitlementEntity entitlement "
            + "set entitlement.supersededSubscriptionCancelledAt = :cancelledAt, "
            + "entitlement.updatedAt = :cancelledAt "
            + "where entitlement.id = :entitlementId "
            + "and entitlement.supersededSubscriptionId = :subscriptionId "
            + "and entitlement.supersededSubscriptionCancelledAt is null")
    int markSupersededSubscriptionCancelled(
            @Param("entitlementId") UUID entitlementId,
            @Param("subscriptionId") String subscriptionId,
            @Param("cancelledAt") Instant cancelledAt);
}
