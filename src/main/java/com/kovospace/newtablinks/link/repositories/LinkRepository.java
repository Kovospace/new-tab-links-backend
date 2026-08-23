package com.kovospace.newtablinks.link.repositories;

import com.kovospace.newtablinks.link.models.LinkEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Persistence access for {@link LinkEntity}.
 *
 * @since 0.0.1
 */
@Repository
public interface LinkRepository extends JpaRepository<LinkEntity, UUID> {

    /**
     * Lists the links sitting directly in a group, excluding those nested in its subgroups.
     *
     * @param groupId identifier of the owning group
     * @return the group's direct links ordered by ascending position, empty when there are none
     */
    List<LinkEntity> findAllByParentGroupIdAndParentSubgroupIsNullOrderByPositionAsc(UUID groupId);

    /**
     * Lists the links nested in a subgroup.
     *
     * @param subgroupId identifier of the owning subgroup
     * @return the subgroup's links ordered by ascending position, empty when there are none
     */
    List<LinkEntity> findAllByParentSubgroupIdOrderByPositionAsc(UUID subgroupId);

    /**
     * Lists every link belonging to the given user, in display order.
     *
     * @param ownerId identifier of the owning user
     * @return the user's links ordered for display
     */
    @Query("select link from LinkEntity link "
            + "where link.parentGroup.environment.owner.id = :ownerId "
            + "order by link.parentGroup.position asc, link.position asc")
    List<LinkEntity> findAllByOwnerIdOrderedForDisplay(@Param("ownerId") UUID ownerId);

    /**
     * Returns the highest position currently used among the links sitting directly in a group.
     *
     * @param groupId identifier of the owning group
     * @return the highest position, or {@code null} when the group has no direct links
     */
    @Query("select max(link.position) from LinkEntity link "
            + "where link.parentGroup.id = :groupId and link.parentSubgroup is null")
    Integer findHighestPositionAmongDirectGroupLinks(@Param("groupId") UUID groupId);

    /**
     * Returns the highest position currently used among the links nested in a subgroup.
     *
     * @param subgroupId identifier of the owning subgroup
     * @return the highest position, or {@code null} when the subgroup has no links
     */
    @Query("select max(link.position) from LinkEntity link where link.parentSubgroup.id = :subgroupId")
    Integer findHighestPositionAmongSubgroupLinks(@Param("subgroupId") UUID subgroupId);
}
