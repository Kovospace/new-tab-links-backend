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
 * The secret behind the activation link mailed after a classic registration.
 *
 * <p>Single use and short lived. Only the hash is stored; the token itself exists only inside the
 * link that was mailed.</p>
 *
 * @since 0.0.2
 */
@Entity
@Table(
        name = "user_activation_token",
        indexes = @Index(name = "ix_activation_token_hash", columnList = "token_hash"))
public class ActivationTokenEntity extends AbstractAuditableEntity {

    /**
     * Account this token activates.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    /**
     * Hash of the token carried by the activation link.
     */
    @Column(name = "token_hash", nullable = false, length = 100)
    private String tokenHash;

    /**
     * Moment after which the link stops working.
     */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /**
     * Moment the link was followed, or {@code null} while it is still unused.
     */
    @Column(name = "consumed_at")
    private Instant consumedAt;

    /**
     * Required by JPA.
     */
    protected ActivationTokenEntity() {
    }

    /**
     * Mints an activation token.
     *
     * @param user      account the token activates
     * @param tokenHash hash of the generated token
     * @param expiresAt moment after which the link stops working
     */
    public ActivationTokenEntity(
            final UserEntity user,
            final String tokenHash,
            final Instant expiresAt) {

        this.user = user;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    /**
     * Returns the account this token activates.
     *
     * @return the account
     */
    public UserEntity getUser() {
        return user;
    }

    /**
     * Tells whether the token may still be used.
     *
     * @param now moment to judge against
     * @return {@code true} when the token is neither spent nor expired
     */
    public boolean isUsableAt(final Instant now) {
        return consumedAt == null && now.isBefore(expiresAt);
    }

    /**
     * Marks the token as spent.
     *
     * @param consumedAt moment of use
     */
    public void markConsumed(final Instant consumedAt) {
        this.consumedAt = consumedAt;
    }
}
