package com.kovospace.newtablinks.common.services;

import com.kovospace.newtablinks.environment.models.EnvironmentEntity;
import com.kovospace.newtablinks.environment.repositories.EnvironmentRepository;
import com.kovospace.newtablinks.group.models.GroupEntity;
import com.kovospace.newtablinks.group.repositories.GroupRepository;
import com.kovospace.newtablinks.link.repositories.LinkRepository;
import com.kovospace.newtablinks.profile.models.ProfileEntity;
import com.kovospace.newtablinks.profile.repositories.ProfileRepository;
import com.kovospace.newtablinks.subgroup.models.SubgroupEntity;
import com.kovospace.newtablinks.subgroup.repositories.SubgroupRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Removes a record together with everything that hangs beneath it.
 *
 * <p>Profile, environment, group, subgroup and link form one containment hierarchy, and every
 * child names its parent through a non-null foreign key. No entity declares the reverse
 * association, so nothing cascades in the mapping. Deleting a parent used to work anyway, but
 * only because of a rule written somewhere this code cannot see: the migrated schema declares
 * {@code ON DELETE CASCADE} on each of those keys, and the database was quietly doing the work.
 * </p>
 *
 * <p>That is not a dependency worth keeping implicit. The schema lives in its own repository,
 * {@code new-tab-links-migrations}, and can be changed without this one being rebuilt; the
 * application runs {@code ddl-auto=validate}, which checks columns and types but <em>not</em>
 * foreign key delete rules, so dropping the cascade would break deletion with nothing failing at
 * startup to say so. Locally the mismatch is already real: a schema Hibernate generates itself
 * has no delete rules at all, so the same code that works deployed raises a constraint violation
 * on a developer's machine - and inside a pushed batch, applied in one transaction, that one
 * violation takes every other change the device sent with it.</p>
 *
 * <p>The alternative would have been {@code @OneToMany(orphanRemoval = true)} on each parent.
 * That was not taken: it makes every module's entity depend on the module below it in both
 * directions, and it changes how those entities load everywhere else, for the sake of one
 * operation. Walking down explicitly costs a handful of queries on a path that runs when a user
 * deletes something, and leaves the rest of the mapping alone.</p>
 *
 * <p>Deletion is always deepest first, so no statement ever removes a row something still points
 * at. Callers are responsible for having established ownership: nothing here checks who the
 * caller is, because every entry point already resolved the entity through an owner-scoped
 * lookup.</p>
 *
 * @since 0.0.7
 */
@Service
public class HierarchyDeletionService {

    private final ProfileRepository profileRepository;
    private final EnvironmentRepository environmentRepository;
    private final GroupRepository groupRepository;
    private final SubgroupRepository subgroupRepository;
    private final LinkRepository linkRepository;

    /**
     * Creates the service.
     *
     * @param profileRepository     persistence access for profiles
     * @param environmentRepository persistence access for environments
     * @param groupRepository       persistence access for groups
     * @param subgroupRepository    persistence access for subgroups
     * @param linkRepository        persistence access for links
     */
    public HierarchyDeletionService(
            final ProfileRepository profileRepository,
            final EnvironmentRepository environmentRepository,
            final GroupRepository groupRepository,
            final SubgroupRepository subgroupRepository,
            final LinkRepository linkRepository) {

        this.profileRepository = profileRepository;
        this.environmentRepository = environmentRepository;
        this.groupRepository = groupRepository;
        this.subgroupRepository = subgroupRepository;
        this.linkRepository = linkRepository;
    }

    /**
     * Deletes a profile, its environments, and everything inside them.
     *
     * @param profile the profile to remove; already resolved as the caller's own
     */
    @Transactional
    public void deleteProfileWithDescendants(final ProfileEntity profile) {

        final List<EnvironmentEntity> environments =
                environmentRepository.findAllByProfileId(profile.getId());

        environments.forEach(this::deleteEnvironmentWithDescendants);
        profileRepository.delete(profile);
    }

    /**
     * Deletes an environment, its groups, and everything inside them.
     *
     * @param environment the environment to remove; already resolved as the caller's own
     */
    @Transactional
    public void deleteEnvironmentWithDescendants(final EnvironmentEntity environment) {

        final List<GroupEntity> groups =
                groupRepository.findAllByEnvironmentIdOrderByPositionAsc(environment.getId());

        groups.forEach(this::deleteGroupWithDescendants);
        environmentRepository.delete(environment);
    }

    /**
     * Deletes a group, its subgroups, and every link in either.
     *
     * <p>Links are cleared in one sweep rather than subgroup by subgroup, because a link nested
     * in a subgroup still names the group as well and would otherwise be left behind holding the
     * group in place.</p>
     *
     * @param group the group to remove; already resolved as the caller's own
     */
    @Transactional
    public void deleteGroupWithDescendants(final GroupEntity group) {

        linkRepository.deleteAll(linkRepository.findAllByParentGroupId(group.getId()));
        subgroupRepository.deleteAll(
                subgroupRepository.findAllByParentGroupIdOrderByPositionAsc(group.getId()));
        groupRepository.delete(group);
    }

    /**
     * Deletes a subgroup and the links nested in it.
     *
     * @param subgroup the subgroup to remove; already resolved as the caller's own
     */
    @Transactional
    public void deleteSubgroupWithDescendants(final SubgroupEntity subgroup) {

        linkRepository.deleteAll(
                linkRepository.findAllByParentSubgroupIdOrderByPositionAsc(subgroup.getId()));
        subgroupRepository.delete(subgroup);
    }
}
