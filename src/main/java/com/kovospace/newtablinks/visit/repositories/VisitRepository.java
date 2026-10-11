package com.kovospace.newtablinks.visit.repositories;

import com.kovospace.newtablinks.visit.models.VisitEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Persistence access for {@link VisitEntity}.
 *
 * @since 0.0.19
 */
@Repository
public interface VisitRepository extends JpaRepository<VisitEntity, UUID> {

    /**
     * Lists every visit of one profile, in no particular order.
     *
     * <p>Used when the profile is being removed: these rows hold it in place and have to be
     * cleared first. Unordered deliberately - nothing reads them, they are only deleted.</p>
     *
     * @param profileId identifier of the owning profile
     * @return the profile's visits, empty when it has none
     */
    List<VisitEntity> findAllByProfileId(UUID profileId);

    /**
     * Lists every visit belonging to the given user, most recent first.
     *
     * <p>The order the browser extension's history panel renders them in, so the snapshot hands
     * them over already sorted.</p>
     *
     * @param ownerId identifier of the owning user
     * @return the user's visits, newest first, empty when there are none
     */
    @Query("select visit from VisitEntity visit "
            + "where visit.profile.owner.id = :ownerId "
            + "order by visit.visitedAt desc")
    List<VisitEntity> findAllByOwnerIdOrderedByMostRecentlyVisited(
            @Param("ownerId") UUID ownerId);

    /**
     * Finds one visit, but only if it belongs to the given owner.
     *
     * <p>Ownership is part of the query rather than a check performed afterwards, for the reason
     * given on
     * {@link com.kovospace.newtablinks.environment.repositories.EnvironmentRepository#findByIdAndOwnerId}:
     * a row that is not the caller's must be indistinguishable from one that does not exist.</p>
     *
     * @param visitId identifier of the visit
     * @param ownerId identifier of the user that must own it
     * @return the visit, or an empty optional when it does not exist or is not theirs
     */
    @Query("select visit from VisitEntity visit "
            + "where visit.id = :visitId and visit.profile.owner.id = :ownerId")
    Optional<VisitEntity> findByIdAndOwnerId(
            @Param("visitId") UUID visitId, @Param("ownerId") UUID ownerId);

    /**
     * Lists the identifiers of an account's visits, most recent first.
     *
     * <p>Ties on the visiting moment are broken by identifier, so that which entry is the oldest
     * is decided the same way on every call.</p>
     *
     * @param ownerId identifier of the owning user
     * @return the identifiers, newest first; empty when the account has none
     */
    @Query("select visit.id from VisitEntity visit "
            + "where visit.profile.owner.id = :ownerId "
            + "order by visit.visitedAt desc, visit.id desc")
    List<UUID> findIdsByOwnerIdNewestFirst(@Param("ownerId") UUID ownerId);

    /**
     * Counts an account's browsing history entries, across all of its profiles.
     *
     * @param ownerId identifier of the owning user
     * @return how many entries the account holds
     */
    long countByProfileOwnerId(UUID ownerId);
}
