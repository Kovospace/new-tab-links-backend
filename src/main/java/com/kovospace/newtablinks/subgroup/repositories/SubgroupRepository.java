package com.kovospace.newtablinks.subgroup.repositories;

import com.kovospace.newtablinks.subgroup.models.SubgroupEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Persistence access for {@link SubgroupEntity}.
 *
 * @since 0.0.1
 */
@Repository
public interface SubgroupRepository extends JpaRepository<SubgroupEntity, UUID> {

    /**
     * Lists a group's subgroups in the order they are displayed.
     *
     * @param groupId identifier of the owning group
     * @return the group's subgroups ordered by ascending position, empty when there are none
     */
    List<SubgroupEntity> findAllByParentGroupIdOrderByPositionAsc(UUID groupId);

    /**
     * Lists every subgroup belonging to any group of the given user, in display order.
     *
     * @param ownerId identifier of the owning user
     * @return the user's subgroups ordered for display
     */
    @Query("select subgroup from SubgroupEntity subgroup "
            + "where subgroup.parentGroup.environment.owner.id = :ownerId "
            + "order by subgroup.parentGroup.position asc, subgroup.position asc")
    List<SubgroupEntity> findAllByOwnerIdOrderedForDisplay(@Param("ownerId") UUID ownerId);

    /**
     * Returns the highest position currently used among a group's subgroups.
     *
     * @param groupId identifier of the owning group
     * @return the highest position, or {@code null} when the group has no subgroups
     */
    @Query("select max(subgroup.position) from SubgroupEntity subgroup "
            + "where subgroup.parentGroup.id = :groupId")
    Integer findHighestPositionByGroupId(@Param("groupId") UUID groupId);
}
