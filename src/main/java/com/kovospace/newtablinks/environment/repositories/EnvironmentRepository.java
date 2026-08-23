package com.kovospace.newtablinks.environment.repositories;

import com.kovospace.newtablinks.environment.models.EnvironmentEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Persistence access for {@link EnvironmentEntity}.
 *
 * @since 0.0.1
 */
@Repository
public interface EnvironmentRepository extends JpaRepository<EnvironmentEntity, UUID> {

    /**
     * Lists a user's environments in the order they are displayed.
     *
     * @param ownerId identifier of the owning user
     * @return the owner's environments ordered by ascending position, empty when there are none
     */
    List<EnvironmentEntity> findAllByOwnerIdOrderByPositionAsc(UUID ownerId);

    /**
     * Returns the highest position currently used among a user's environments.
     *
     * @param ownerId identifier of the owning user
     * @return the highest position, or {@code null} when the user has no environments
     */
    @Query(
            "select max(environment.position) from EnvironmentEntity environment "
                    + "where environment.owner.id = :ownerId")
    Integer findHighestPositionByOwnerId(@Param("ownerId") UUID ownerId);
}
