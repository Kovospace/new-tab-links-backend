package com.kovospace.newtablinks.profile.repositories;

import com.kovospace.newtablinks.profile.models.ProfileEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Persistence access for {@link ProfileEntity}.
 *
 * @since 0.0.6
 */
@Repository
public interface ProfileRepository extends JpaRepository<ProfileEntity, UUID> {

    /**
     * Lists a user's profiles in the order they are displayed.
     *
     * @param ownerId identifier of the owning user
     * @return the owner's profiles ordered by ascending position, empty when there are none
     */
    List<ProfileEntity> findAllByOwnerIdOrderByPositionAsc(UUID ownerId);

    /**
     * Finds one profile, but only if it belongs to the given owner.
     *
     * <p>Ownership is part of the query rather than a check performed afterwards, for the reason
     * given on
     * {@link com.kovospace.newtablinks.environment.repositories.EnvironmentRepository#findByIdAndOwnerId}:
     * a row that is not the caller's must be indistinguishable from one that does not exist.</p>
     *
     * @param profileId identifier of the profile
     * @param ownerId   identifier of the user that must own it
     * @return the profile, or an empty optional when it does not exist or is not theirs
     */
    Optional<ProfileEntity> findByIdAndOwnerId(UUID profileId, UUID ownerId);

    /**
     * Returns the highest position currently used among a user's profiles.
     *
     * @param ownerId identifier of the owning user
     * @return the highest position, or {@code null} when the user has no profiles
     */
    @Query("select max(profile.position) from ProfileEntity profile "
            + "where profile.owner.id = :ownerId")
    Integer findHighestPositionByOwnerId(@Param("ownerId") UUID ownerId);

    /**
     * Counts an account's profiles.
     *
     * @param ownerId identifier of the owning user
     * @return how many profiles the account has
     * @since 0.0.16
     */
    long countByOwnerId(UUID ownerId);

    /**
     * Lists the identifiers of an account's first profiles, in synchronisation slot order: the
     * order the server first stored them, ties broken by identifier.
     *
     * <p>The identifier tie-break compares as PostgreSQL orders {@code uuid} - byte by byte, the
     * same order as the lowercase text form - which is what a client sorting the listed
     * {@code createdAt} and {@code id} as strings reproduces.</p>
     *
     * @param ownerId   identifier of the owning user
     * @param slotCount how many of the first profiles to return
     * @return at most {@code slotCount} identifiers, first stored first
     * @since 0.0.18
     */
    @Query("select profile.id from ProfileEntity profile where profile.owner.id = :ownerId "
            + "order by profile.createdAt asc, profile.id asc")
    List<UUID> findIdsOfOwnerInSlotOrder(@Param("ownerId") UUID ownerId, Limit slotCount);
}
