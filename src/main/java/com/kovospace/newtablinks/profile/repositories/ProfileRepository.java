package com.kovospace.newtablinks.profile.repositories;

import com.kovospace.newtablinks.profile.models.ProfileEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
}
