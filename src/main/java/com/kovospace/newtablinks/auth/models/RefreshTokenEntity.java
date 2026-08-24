package com.kovospace.newtablinks.auth.models;

import com.kovospace.newtablinks.common.models.AbstractAuditableEntity;
import com.kovospace.newtablinks.user.models.UserDeviceEntity;
import com.kovospace.newtablinks.user.models.UserEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A long lived credential that lets one device mint fresh access tokens without a password.
 *
 * <p>Only the hash is stored. Access tokens are deliberately not stored at all: they are
 * self-contained, short lived and verified by signature. This table is the only thing that makes
 * a session revocable, which is why refreshing rotates the row rather than reusing it.</p>
 *
 * <p>Rotation means these rows are short lived and numerous, so they are not what a user is shown
 * - {@link UserDeviceEntity} is. Every token belongs to a device, and revoking a device revokes
 * its tokens.</p>
 *
 * @since 0.0.2
 */
@Entity
@Table(
        name = "refresh_token",
        indexes = @Index(name = "ix_refresh_token_hash", columnList = "token_hash"))
public class RefreshTokenEntity extends AbstractAuditableEntity {

    /**
     * Account this token belongs to.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    /**
     * Device this token was issued to.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    private UserDeviceEntity device;

    /**
     * Hash of the token handed to the client.
     */
    @Column(name = "token_hash", nullable = false, length = 100)
    private String tokenHash;

    /**
     * Moment after which the token is refused.
     */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /**
     * Moment the token was revoked, or {@code null} while it is still valid.
     */
    @Column(name = "revoked_at")
    private Instant revokedAt;

    /**
     * Required by JPA.
     */
    protected RefreshTokenEntity() {
    }

    /**
     * Issues a refresh token.
     *
     * @param user      account the token belongs to
     * @param device    device the token is issued to
     * @param tokenHash hash of the generated token
     * @param expiresAt moment after which the token is refused
     */
    public RefreshTokenEntity(
            final UserEntity user,
            final UserDeviceEntity device,
            final String tokenHash,
            final Instant expiresAt) {

        this.user = user;
        this.device = device;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    /**
     * Returns the account this token belongs to.
     *
     * @return the account
     */
    public UserEntity getUser() {
        return user;
    }

    /**
     * Returns the device this token was issued to.
     *
     * @return the device
     */
    public UserDeviceEntity getDevice() {
        return device;
    }

    /**
     * Tells whether the token may still be exchanged for an access token.
     *
     * @param now moment to judge against
     * @return {@code true} when the token is neither revoked nor expired
     */
    public boolean isUsableAt(final Instant now) {
        return revokedAt == null && now.isBefore(expiresAt);
    }

    /**
     * Revokes the token, ending that device's session.
     *
     * @param revokedAt moment of revocation
     */
    public void revoke(final Instant revokedAt) {
        this.revokedAt = revokedAt;
    }
}
