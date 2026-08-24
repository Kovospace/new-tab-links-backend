package com.kovospace.newtablinks.auth.repositories;

import com.kovospace.newtablinks.auth.models.EmailedTokenEntity;
import com.kovospace.newtablinks.auth.models.EmailedTokenPurpose;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Persistence access for {@link EmailedTokenEntity}.
 *
 * @since 0.0.3
 */
@Repository
public interface EmailedTokenRepository extends JpaRepository<EmailedTokenEntity, UUID> {

    /**
     * Finds a mailed token by its hash, within one purpose.
     *
     * @param tokenHash hash of the token the caller presented
     * @param purpose   redemption the caller is attempting
     * @return the matching token, or an empty optional when there is none
     */
    Optional<EmailedTokenEntity> findByTokenHashAndPurpose(
            String tokenHash, EmailedTokenPurpose purpose);
}
