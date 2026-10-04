package com.kovospace.newtablinks.link.repositories;

import com.kovospace.newtablinks.common.models.ContainerItemCount;
import com.kovospace.newtablinks.link.models.LinkEntity;
import java.util.List;
import java.util.Optional;
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
     * Lists every link belonging to a group, including the ones nested in its subgroups.
     *
     * <p>Used when a group is being removed: a link is attached to its group whether or not it
     * also sits in a subgroup, so this is what has to be cleared before the group can go.</p>
     *
     * @param groupId identifier of the owning group
     * @return the group's links, empty when it has none
     */
    List<LinkEntity> findAllByParentGroupId(UUID groupId);

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
     * Finds one link, but only if it belongs to the given owner.
     *
     * @param linkId  identifier of the link
     * @param ownerId identifier of the user that must own it
     * @return the link, or an empty optional when it does not exist or is not theirs
     */
    @Query("select link from LinkEntity link "
            + "where link.id = :linkId and link.parentGroup.environment.owner.id = :ownerId")
    Optional<LinkEntity> findByIdAndOwnerId(
            @Param("linkId") UUID linkId, @Param("ownerId") UUID ownerId);

    /**
     * Lists a group's direct links in display order, but only if the group is the owner's.
     *
     * @param parentGroupId identifier of the owning group
     * @param ownerId       identifier of the user that must own it
     * @return the links, empty when there are none or the group is not theirs
     */
    @Query("select link from LinkEntity link "
            + "where link.parentGroup.id = :parentGroupId and link.parentSubgroup is null "
            + "and link.parentGroup.environment.owner.id = :ownerId "
            + "order by link.position asc")
    List<LinkEntity> findDirectGroupLinksForOwner(
            @Param("parentGroupId") UUID parentGroupId, @Param("ownerId") UUID ownerId);

    /**
     * Lists a subgroup's links in display order, but only if the subgroup is the owner's.
     *
     * @param parentSubgroupId identifier of the owning subgroup
     * @param ownerId          identifier of the user that must own it
     * @return the links, empty when there are none or the subgroup is not theirs
     */
    @Query("select link from LinkEntity link "
            + "where link.parentSubgroup.id = :parentSubgroupId "
            + "and link.parentGroup.environment.owner.id = :ownerId "
            + "order by link.position asc")
    List<LinkEntity> findSubgroupLinksForOwner(
            @Param("parentSubgroupId") UUID parentSubgroupId, @Param("ownerId") UUID ownerId);

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

    /**
     * Counts the links of one environment, through every group and subgroup of it.
     *
     * @param environmentId identifier of the environment, already resolved for its owner
     * @return how many links the environment holds
     * @since 0.0.16
     */
    @Query("select count(link) from LinkEntity link "
            + "where link.parentGroup.environment.id = :environmentId")
    long countByEnvironmentId(@Param("environmentId") UUID environmentId);

    /**
     * Counts the links of every environment of an account that holds at least one.
     *
     * @param ownerId identifier of the owning user
     * @return one entry per environment with links; an environment without any is absent
     * @since 0.0.16
     */
    @Query("select new com.kovospace.newtablinks.common.models.ContainerItemCount("
            + "link.parentGroup.environment.id, count(link)) from LinkEntity link "
            + "where link.parentGroup.environment.owner.id = :ownerId "
            + "group by link.parentGroup.environment.id")
    List<ContainerItemCount> countLinksPerEnvironmentOfOwner(@Param("ownerId") UUID ownerId);
}
