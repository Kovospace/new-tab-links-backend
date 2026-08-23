package com.kovospace.newtablinks.auth.models;

import com.kovospace.newtablinks.common.models.AbstractAuditableEntity;
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
 * A long lived credential that lets a client mint fresh access tokens without a password.
 *
 * <p>One row per signed-in client, so a single browser extension install can be revoked without
 * disturbing the user's other devices. Only the hash is stored.</p>
 *
 * <p>Access tokens are deliberately not stored anywhere: they are self-contained, short lived and
 * verified by signature. This table is the only thing that makes a session revocable, which is
 * why refreshing rotates the row rather than reusing it.</p>
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
     * Free text describing the client that obtained the token, for the user's own session list.
     */
    @Column(name = "client_description", length = 255)
    private String clientDescription;

    /**
     * Required by JPA.
     */
    protected RefreshTokenEntity() {
    }

    /**
     * Issues a refresh token.
     *
     * @param user              account the token belongs to
     * @param tokenHash         hash of the generated token
     * @param expiresAt         moment after which the token is refused
     * @param clientDescription description of the client, may be {@code null}
     */
    public RefreshTokenEntity(
            final UserEntity user,
            final String tokenHash,
            final Instant expiresAt,
            final String clientDescription) {

        this.user = user;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.clientDescription = clientDescription;
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
     * Tells whether the token may still be exchanged for an access token.
     *
     * @param now moment to judge against
     * @return {@code true} when the token is neither revoked nor expired
     */
    public boolean isUsableAt(final Instant now) {
        return revokedAt == null && now.isBefore(expiresAt);
    }

    /**
     * Revokes the token, ending that client's session.
     *
     * @param revokedAt moment of revocation
     */
    public void revoke(final Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    /**
     * Returns the description of the client that obtained the token.
     *
     * @return the description, or {@code null} when none was recorded
     */
    public String getClientDescription() {
        return clientDescription;
    }
}
