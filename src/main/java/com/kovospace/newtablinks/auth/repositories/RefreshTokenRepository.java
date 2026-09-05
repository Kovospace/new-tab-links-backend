package com.kovospace.newtablinks.auth.repositories;

import com.kovospace.newtablinks.auth.models.RefreshTokenEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Persistence access for {@link RefreshTokenEntity}.
 *
 * @since 0.0.2
 */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {

    /**
     * Finds a refresh token by its hash.
     *
     * @param tokenHash hash of the token the client presented
     * @return the matching token, or an empty optional when there is none
     */
    Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);

    /**
     * Lists the tokens of one account that have not been revoked.
     *
     * @param userId identifier of the account
     * @return the account's live tokens, empty when it has none
     */
    List<RefreshTokenEntity> findAllByUserIdAndRevokedAtIsNull(UUID userId);

    /**
     * Revokes every live token issued to one device, signing that browser out.
     *
     * @param deviceId  identifier of the device
     * @param revokedAt moment to record as the revocation time
     * @return how many tokens were revoked
     */
    @Modifying
    @Query("update RefreshTokenEntity refreshToken set refreshToken.revokedAt = :revokedAt "
            + "where refreshToken.device.id = :deviceId and refreshToken.revokedAt is null")
    int revokeAllLiveTokensOfDevice(
            @Param("deviceId") UUID deviceId, @Param("revokedAt") Instant revokedAt);

    /**
     * Revokes every live token of one account in a single statement.
     *
     * <p>Used when a password changes or the user signs out everywhere.</p>
     *
     * @param userId    identifier of the account
     * @param revokedAt moment to record as the revocation time
     * @return how many tokens were revoked
     */
    @Modifying
    @Query("update RefreshTokenEntity refreshToken set refreshToken.revokedAt = :revokedAt "
            + "where refreshToken.user.id = :userId and refreshToken.revokedAt is null")
    int revokeAllLiveTokensOfUser(@Param("userId") UUID userId, @Param("revokedAt") Instant revokedAt);

    /**
     * Lists every refresh token issued to a user, revoked ones included.
     *
     * <p>Distinct from {@link #findAllByUserIdAndRevokedAtIsNull(UUID)}, which answers "which
     * sessions are live". This one exists for account deletion, where a revoked row still holds
     * a foreign key and still has to go.</p>
     *
     * @param userId identifier of the owning user
     * @return the user's refresh tokens, empty when there are none
     */
    List<RefreshTokenEntity> findAllByUserId(UUID userId);
}
