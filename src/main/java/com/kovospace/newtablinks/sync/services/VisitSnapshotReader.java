package com.kovospace.newtablinks.sync.services;

import com.kovospace.newtablinks.visit.dtos.VisitDto;
import com.kovospace.newtablinks.visit.mappers.VisitMapper;
import com.kovospace.newtablinks.visit.repositories.VisitRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Supplies the browsing history part of a synchronization snapshot.
 *
 * <p>A reader of its own for the reason {@link ClosedTabSnapshotReader} is one.</p>
 *
 * @since 0.0.19
 */
@Component
public class VisitSnapshotReader {

    private final VisitRepository visitRepository;
    private final VisitMapper visitMapper;

    /**
     * Creates the reader.
     *
     * @param visitRepository reads the owner's visits
     * @param visitMapper     converts them
     */
    public VisitSnapshotReader(
            final VisitRepository visitRepository,
            final VisitMapper visitMapper) {

        this.visitRepository = visitRepository;
        this.visitMapper = visitMapper;
    }

    /**
     * Reads a user's visits, most recent first.
     *
     * <p>Never {@code null}: an account with no history gets an empty list, and the extension
     * reads the presence of the collection as "this server keeps visits".</p>
     *
     * @param ownerId identifier of the owning user
     * @return the owner's visits, newest first, empty when there are none
     */
    public List<VisitDto> readVisitsOfOwner(final UUID ownerId) {
        return visitMapper.toDtoList(
                visitRepository.findAllByOwnerIdOrderedByMostRecentlyVisited(ownerId));
    }
}
