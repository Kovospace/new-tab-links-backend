package com.kovospace.newtablinks.sync.services;

import com.kovospace.newtablinks.group.mappers.GroupMapper;
import com.kovospace.newtablinks.group.repositories.GroupRepository;
import com.kovospace.newtablinks.link.mappers.LinkMapper;
import com.kovospace.newtablinks.link.repositories.LinkRepository;
import com.kovospace.newtablinks.profile.services.ProfileService;
import com.kovospace.newtablinks.subgroup.mappers.SubgroupMapper;
import com.kovospace.newtablinks.subgroup.repositories.SubgroupRepository;
import com.kovospace.newtablinks.sync.dtos.SyncSnapshotDto;
import com.kovospace.newtablinks.user.services.AccountPlanLimitsService;
import com.kovospace.newtablinks.user.services.UserService;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assembles the whole-account snapshot a client pulls when it synchronizes.
 *
 * <p>This service owns no data of its own; it composes what the domain modules already expose.
 * It reads each level with one query rather than walking the tree, so the cost of a snapshot is
 * a fixed handful of queries regardless of how much the user has stored.</p>
 *
 * <p><strong>Read only for now.</strong> Accepting changes from a client needs answers this
 * service cannot invent - how conflicts are resolved, how deletions travel, whether the client or
 * the server assigns identifiers - so pushing is left to the assignment that designs the sync
 * protocol.</p>
 *
 * @since 0.0.1
 */
@Service
public class SyncSnapshotService {

    private final UserService userService;
    private final GroupRepository groupRepository;
    private final GroupMapper groupMapper;
    private final SubgroupRepository subgroupRepository;
    private final SubgroupMapper subgroupMapper;
    private final LinkRepository linkRepository;
    private final LinkMapper linkMapper;
    private final EnvironmentSnapshotReader environmentSnapshotReader;
    private final ClosedTabSnapshotReader closedTabSnapshotReader;
    private final ProfileService profileService;
    private final AccountPlanLimitsService accountPlanLimitsService;

    /**
     * Creates the service.
     *
     * @param userService               reads the owner, with whether it is pro
     * @param groupRepository           reads every group of the owner
     * @param groupMapper               converts groups
     * @param subgroupRepository        reads every subgroup of the owner
     * @param subgroupMapper            converts subgroups
     * @param linkRepository            reads every link of the owner
     * @param linkMapper                converts links
     * @param environmentSnapshotReader reads the owner's environments
     * @param closedTabSnapshotReader   reads the owner's closed tabs
     * @param profileService            reads the owner's profiles
     * @param accountPlanLimitsService  reads the limits that hold for the owner
     */
    public SyncSnapshotService(
            final UserService userService,
            final GroupRepository groupRepository,
            final GroupMapper groupMapper,
            final SubgroupRepository subgroupRepository,
            final SubgroupMapper subgroupMapper,
            final LinkRepository linkRepository,
            final LinkMapper linkMapper,
            final EnvironmentSnapshotReader environmentSnapshotReader,
            final ClosedTabSnapshotReader closedTabSnapshotReader,
            final ProfileService profileService,
            final AccountPlanLimitsService accountPlanLimitsService) {

        this.userService = userService;
        this.groupRepository = groupRepository;
        this.groupMapper = groupMapper;
        this.subgroupRepository = subgroupRepository;
        this.subgroupMapper = subgroupMapper;
        this.linkRepository = linkRepository;
        this.linkMapper = linkMapper;
        this.environmentSnapshotReader = environmentSnapshotReader;
        this.closedTabSnapshotReader = closedTabSnapshotReader;
        this.profileService = profileService;
        this.accountPlanLimitsService = accountPlanLimitsService;
    }

    /**
     * Assembles the snapshot of everything one user owns.
     *
     * @param ownerId identifier of the user to snapshot
     * @return the snapshot
     * @throws com.kovospace.newtablinks.common.exceptions.ResourceNotFoundException when no user
     *                                                                              has that identifier
     */
    @Transactional(readOnly = true)
    public SyncSnapshotDto captureSnapshotForUser(final UUID ownerId) {
        return new SyncSnapshotDto(
                userService.findUserById(ownerId),
                profileService.findProfilesByOwner(ownerId),
                environmentSnapshotReader.readEnvironmentsOfOwner(ownerId),
                groupMapper.toDtoList(groupRepository.findAllByOwnerIdOrderedForDisplay(ownerId)),
                subgroupMapper.toDtoList(subgroupRepository.findAllByOwnerIdOrderedForDisplay(ownerId)),
                linkMapper.toDtoList(linkRepository.findAllByOwnerIdOrderedForDisplay(ownerId)),
                closedTabSnapshotReader.readClosedTabsOfOwner(ownerId),
                Instant.now(),
                accountPlanLimitsService.findPlanLimitsOf(ownerId));
    }
}
