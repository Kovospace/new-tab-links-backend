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

    /**
     * Lists every token issued to one device, revoked ones included.
     *
     * <p>Used when one installation takes a device row over from another: the tokens the taker
     * already holds have to move with it, or the row they point at disappears under them.</p>
     *
     * @param deviceId identifier of the device
     * @return the device's tokens, empty when it has none
     */
    List<RefreshTokenEntity> findAllByDeviceId(UUID deviceId);

    /**
     * Tells whether one device holds a session right now - a refresh token neither revoked nor
     * expired.
     *
     * @param deviceId identifier of the device
     * @param now      the moment to judge expiry at
     * @return {@code true} when the device is signed in
     * @since 0.0.18
     */
    @Query("select count(refreshToken) > 0 from RefreshTokenEntity refreshToken "
            + "where refreshToken.device.id = :deviceId and refreshToken.revokedAt is null "
            + "and refreshToken.expiresAt > :now")
    boolean existsLiveTokenOfDevice(@Param("deviceId") UUID deviceId, @Param("now") Instant now);

    /**
     * Counts an account's extension installations signed in right now, other than one.
     *
     * <p>An installation is a device that reported an installation identifier; signed in means
     * it holds a refresh token neither revoked nor expired. The website's own sign-ins report no
     * installation and are never counted.</p>
     *
     * @param userId          identifier of the account
     * @param excludedDeviceId the device asking, left out of the count
     * @param now             the moment to judge expiry at
     * @return how many other installations are signed in
     * @since 0.0.18
     */
    @Query("select count(distinct refreshToken.device.id) from RefreshTokenEntity refreshToken "
            + "where refreshToken.user.id = :userId and refreshToken.revokedAt is null "
            + "and refreshToken.expiresAt > :now "
            + "and refreshToken.device.installationId is not null "
            + "and refreshToken.device.id <> :excludedDeviceId")
    long countSignedInInstallationsOtherThan(
            @Param("userId") UUID userId,
            @Param("excludedDeviceId") UUID excludedDeviceId,
            @Param("now") Instant now);

    /**
     * Counts an account's extension installations signed in right now that first signed in
     * before a given moment - the device rows recorded earlier.
     *
     * @param userId        identifier of the account
     * @param firstSignedIn when the device asking was first recorded
     * @param now           the moment to judge expiry at
     * @return how many signed-in installations precede it
     * @since 0.0.18
     */
    @Query("select count(distinct refreshToken.device.id) from RefreshTokenEntity refreshToken "
            + "where refreshToken.user.id = :userId and refreshToken.revokedAt is null "
            + "and refreshToken.expiresAt > :now "
            + "and refreshToken.device.installationId is not null "
            + "and refreshToken.device.createdAt < :firstSignedIn")
    long countSignedInInstallationsFirstSignedInBefore(
            @Param("userId") UUID userId,
            @Param("firstSignedIn") Instant firstSignedIn,
            @Param("now") Instant now);
}
