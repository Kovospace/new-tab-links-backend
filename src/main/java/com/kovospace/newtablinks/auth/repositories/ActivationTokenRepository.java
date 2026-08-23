package com.kovospace.newtablinks.auth.repositories;

import com.kovospace.newtablinks.auth.models.ActivationTokenEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Persistence access for {@link ActivationTokenEntity}.
 *
 * @since 0.0.2
 */
@Repository
public interface ActivationTokenRepository extends JpaRepository<ActivationTokenEntity, UUID> {

    /**
     * Finds an activation token by its hash.
     *
     * @param tokenHash hash of the token carried by the activation link
     * @return the matching token, or an empty optional when there is none
     */
    Optional<ActivationTokenEntity> findByTokenHash(String tokenHash);
}
