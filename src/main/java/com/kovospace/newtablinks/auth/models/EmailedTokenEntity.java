package com.kovospace.newtablinks.auth.models;

import com.kovospace.newtablinks.common.models.AbstractAuditableEntity;
import com.kovospace.newtablinks.user.models.UserEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A secret sent to a user's address, proving they can read that mailbox.
 *
 * <p>Account activation and password reset are the same mechanism wearing different labels -
 * mint, mail, redeem exactly once, expire - so both live here and are told apart by
 * {@link EmailedTokenPurpose}.</p>
 *
 * <p>Only the hash is stored; the token itself exists only inside the link that was mailed.</p>
 *
 * @since 0.0.3
 */
@Entity
@Table(
        name = "emailed_token",
        indexes = @Index(name = "ix_emailed_token_hash", columnList = "token_hash"))
public class EmailedTokenEntity extends AbstractAuditableEntity {

    /**
     * Account the token acts on.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    /**
     * Hash of the token carried by the mailed link.
     */
    @Column(name = "token_hash", nullable = false, length = 100)
    private String tokenHash;

    /**
     * What the token may be redeemed for.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 40)
    private EmailedTokenPurpose purpose;

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
    protected EmailedTokenEntity() {
    }

    /**
     * Mints a token.
     *
     * @param user      account the token acts on
     * @param tokenHash hash of the generated token
     * @param purpose   what the token may be redeemed for
     * @param expiresAt moment after which the link stops working
     */
    public EmailedTokenEntity(
            final UserEntity user,
            final String tokenHash,
            final EmailedTokenPurpose purpose,
            final Instant expiresAt) {

        this.user = user;
        this.tokenHash = tokenHash;
        this.purpose = purpose;
        this.expiresAt = expiresAt;
    }

    /**
     * Returns the account the token acts on.
     *
     * @return the account
     */
    public UserEntity getUser() {
        return user;
    }

    /**
     * Returns what the token may be redeemed for.
     *
     * @return the purpose
     */
    public EmailedTokenPurpose getPurpose() {
        return purpose;
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
