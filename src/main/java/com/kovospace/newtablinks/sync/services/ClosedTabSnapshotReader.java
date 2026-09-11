package com.kovospace.newtablinks.sync.services;

import com.kovospace.newtablinks.closedtab.dtos.ClosedTabDto;
import com.kovospace.newtablinks.closedtab.mappers.ClosedTabMapper;
import com.kovospace.newtablinks.closedtab.repositories.ClosedTabRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Supplies the closed tab part of a synchronization snapshot.
 *
 * <p>A reader of its own rather than two more constructor arguments on {@link SyncSnapshotService},
 * which already carries one per level of the hierarchy; see {@link EnvironmentSnapshotReader},
 * which exists for the same reason.</p>
 *
 * @since 0.0.8
 */
@Component
public class ClosedTabSnapshotReader {

    private final ClosedTabRepository closedTabRepository;
    private final ClosedTabMapper closedTabMapper;

    /**
     * Creates the reader.
     *
     * @param closedTabRepository reads the owner's closed tabs
     * @param closedTabMapper     converts them
     */
    public ClosedTabSnapshotReader(
            final ClosedTabRepository closedTabRepository,
            final ClosedTabMapper closedTabMapper) {

        this.closedTabRepository = closedTabRepository;
        this.closedTabMapper = closedTabMapper;
    }

    /**
     * Reads a user's closed tabs, most recently closed first.
     *
     * <p>Ordered by the closing moment each client reported, which is the order the extension's
     * panel renders them in and the only order they are ever read in.</p>
     *
     * @param ownerId identifier of the owning user
     * @return the owner's closed tabs, newest first, empty when there are none
     */
    public List<ClosedTabDto> readClosedTabsOfOwner(final UUID ownerId) {
        return closedTabMapper.toDtoList(
                closedTabRepository.findAllByOwnerIdOrderedByMostRecentlyClosed(ownerId));
    }
}
