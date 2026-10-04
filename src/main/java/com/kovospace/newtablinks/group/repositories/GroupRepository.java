package com.kovospace.newtablinks.group.repositories;

import com.kovospace.newtablinks.common.models.ContainerItemCount;
import com.kovospace.newtablinks.group.models.GroupEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Persistence access for {@link GroupEntity}.
 *
 * @since 0.0.1
 */
@Repository
public interface GroupRepository extends JpaRepository<GroupEntity, UUID> {

    /**
     * Lists an environment's groups in the order they are displayed.
     *
     * @param environmentId identifier of the owning environment
     * @return the environment's groups ordered by ascending position, empty when there are none
     */
    List<GroupEntity> findAllByEnvironmentIdOrderByPositionAsc(UUID environmentId);

    /**
     * Lists every group belonging to any environment of the given user, in display order.
     *
     * <p>Used when assembling a whole-account snapshot, to avoid querying group by group.</p>
     *
     * @param ownerId identifier of the owning user
     * @return the user's groups ordered by environment position then group position
     */
    @Query("select linkGroup from GroupEntity linkGroup "
            + "where linkGroup.environment.owner.id = :ownerId "
            + "order by linkGroup.environment.position asc, linkGroup.position asc")
    List<GroupEntity> findAllByOwnerIdOrderedForDisplay(@Param("ownerId") UUID ownerId);

    /**
     * Finds one group, but only if it belongs to the given owner.
     *
     * @param groupId identifier of the group
     * @param ownerId identifier of the user that must own it
     * @return the group, or an empty optional when it does not exist or is not theirs
     */
    @Query("select linkGroup from GroupEntity linkGroup "
            + "where linkGroup.id = :groupId and linkGroup.environment.owner.id = :ownerId")
    Optional<GroupEntity> findByIdAndOwnerId(
            @Param("groupId") UUID groupId, @Param("ownerId") UUID ownerId);

    /**
     * Lists an environment's groups in display order, but only if the environment is the owner's.
     *
     * @param environmentId identifier of the owning environment
     * @param ownerId       identifier of the user that must own it
     * @return the groups, empty when there are none or the environment is not theirs
     */
    @Query("select linkGroup from GroupEntity linkGroup "
            + "where linkGroup.environment.id = :environmentId "
            + "and linkGroup.environment.owner.id = :ownerId "
            + "order by linkGroup.position asc")
    List<GroupEntity> findAllByEnvironmentIdAndOwnerIdOrderByPositionAsc(
            @Param("environmentId") UUID environmentId, @Param("ownerId") UUID ownerId);

    /**
     * Returns the highest position currently used among an environment's groups.
     *
     * @param environmentId identifier of the owning environment
     * @return the highest position, or {@code null} when the environment has no groups
     */
    @Query("select max(linkGroup.position) from GroupEntity linkGroup "
            + "where linkGroup.environment.id = :environmentId")
    Integer findHighestPositionByEnvironmentId(@Param("environmentId") UUID environmentId);

    /**
     * Counts the groups of one environment.
     *
     * @param environmentId identifier of the environment, already resolved for its owner
     * @return how many groups the environment holds
     * @since 0.0.18
     */
    long countByEnvironmentId(UUID environmentId);

    /**
     * Counts the groups of every environment of an account that holds at least one.
     *
     * @param ownerId identifier of the owning user
     * @return one entry per environment with groups; an environment without any is absent
     * @since 0.0.18
     */
    @Query("select new com.kovospace.newtablinks.common.models.ContainerItemCount("
            + "linkGroup.environment.id, count(linkGroup)) from GroupEntity linkGroup "
            + "where linkGroup.environment.owner.id = :ownerId "
            + "group by linkGroup.environment.id")
    List<ContainerItemCount> countGroupsPerEnvironmentOfOwner(@Param("ownerId") UUID ownerId);
}
