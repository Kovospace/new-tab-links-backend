package com.kovospace.newtablinks.closedtab.repositories;

import com.kovospace.newtablinks.closedtab.models.ClosedTabEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Persistence access for {@link ClosedTabEntity}.
 *
 * @since 0.0.8
 */
@Repository
public interface ClosedTabRepository extends JpaRepository<ClosedTabEntity, UUID> {

    /**
     * Lists every closed tab of one profile, in no particular order.
     *
     * <p>Used when the profile is being removed: these rows hold it in place and have to be
     * cleared first. Unordered deliberately - nothing reads them, they are only deleted.</p>
     *
     * @param profileId identifier of the owning profile
     * @return the profile's closed tabs, empty when it has none
     */
    List<ClosedTabEntity> findAllByProfileId(UUID profileId);

    /**
     * Lists every closed tab belonging to the given user, most recently closed first.
     *
     * <p>The order the browser extension's panel renders them in, so the snapshot hands them over
     * already sorted rather than making every client sort them again.</p>
     *
     * @param ownerId identifier of the owning user
     * @return the user's closed tabs, newest first, empty when there are none
     */
    @Query("select closedTab from ClosedTabEntity closedTab "
            + "where closedTab.profile.owner.id = :ownerId "
            + "order by closedTab.closedAt desc")
    List<ClosedTabEntity> findAllByOwnerIdOrderedByMostRecentlyClosed(
            @Param("ownerId") UUID ownerId);

    /**
     * Finds one closed tab, but only if it belongs to the given owner.
     *
     * <p>Ownership is part of the query rather than a check performed afterwards, for the reason
     * given on
     * {@link com.kovospace.newtablinks.environment.repositories.EnvironmentRepository#findByIdAndOwnerId}:
     * a row that is not the caller's must be indistinguishable from one that does not exist.</p>
     *
     * @param closedTabId identifier of the closed tab
     * @param ownerId     identifier of the user that must own it
     * @return the closed tab, or an empty optional when it does not exist or is not theirs
     */
    @Query("select closedTab from ClosedTabEntity closedTab "
            + "where closedTab.id = :closedTabId and closedTab.profile.owner.id = :ownerId")
    Optional<ClosedTabEntity> findByIdAndOwnerId(
            @Param("closedTabId") UUID closedTabId, @Param("ownerId") UUID ownerId);

    /**
     * Lists the identifiers of an account's closed tabs, most recently closed first.
     *
     * <p>Ties on the closing moment are broken by identifier, so that which entry is the oldest
     * is decided the same way on every call.</p>
     *
     * @param ownerId identifier of the owning user
     * @return the identifiers, newest first; empty when the account has none
     * @since 0.0.16
     */
    @Query("select closedTab.id from ClosedTabEntity closedTab "
            + "where closedTab.profile.owner.id = :ownerId "
            + "order by closedTab.closedAt desc, closedTab.id desc")
    List<UUID> findIdsByOwnerIdNewestFirst(@Param("ownerId") UUID ownerId);

    /**
     * Counts an account's closed-tab history entries, across all of its profiles.
     *
     * @param ownerId identifier of the owning user
     * @return how many entries the account holds
     * @since 0.0.18
     */
    long countByProfileOwnerId(UUID ownerId);
}
