package com.kovospace.newtablinks.sync.services;

import com.kovospace.newtablinks.environment.dtos.EnvironmentDto;
import com.kovospace.newtablinks.environment.services.EnvironmentService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Supplies the environment part of a synchronization snapshot.
 *
 * <p>Environments are the one level whose "everything for this owner" query already exists on
 * {@link EnvironmentService}, so this thin adapter reuses it instead of adding another repository
 * dependency to {@link SyncSnapshotService}.</p>
 *
 * @since 0.0.1
 */
@Component
public class EnvironmentSnapshotReader {

    private final EnvironmentService environmentService;

    /**
     * Creates the reader.
     *
     * @param environmentService service owning environment reads
     */
    public EnvironmentSnapshotReader(final EnvironmentService environmentService) {
        this.environmentService = environmentService;
    }

    /**
     * Reads a user's environments in display order.
     *
     * @param ownerId identifier of the owning user
     * @return the owner's environments, empty when there are none
     */
    public List<EnvironmentDto> readEnvironmentsOfOwner(final UUID ownerId) {
        return environmentService.findEnvironmentsByOwner(ownerId);
    }
}
