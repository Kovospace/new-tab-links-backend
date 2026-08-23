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
 * A short lived, single use code that can be exchanged for a token pair.
 *
 * <p>Two different hand-offs need the same mechanics - mint, hand over, redeem exactly once,
 * expire quickly - and differ only in who reads the code and how long it lives, so both are
 * modelled here and told apart by {@link SingleUseCodePurpose}.</p>
 *
 * <p>Only the hash of the code is stored; see
 * {@link com.kovospace.newtablinks.auth.utils.TokenHasher}.</p>
 *
 * @since 0.0.2
 */
@Entity
@Table(
        name = "single_use_code",
        indexes = @Index(name = "ix_single_use_code_hash", columnList = "code_hash"))
public class SingleUseCodeEntity extends AbstractAuditableEntity {

    /**
     * Account the code grants access to.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    /**
     * Hash of the code. The code itself exists only in the response that handed it out.
     */
    @Column(name = "code_hash", nullable = false, length = 100)
    private String codeHash;

    /**
     * What this code may be exchanged for.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 40)
    private SingleUseCodePurpose purpose;

    /**
     * Moment after which the code is refused.
     */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /**
     * Moment the code was redeemed, or {@code null} while it is still unused.
     */
    @Column(name = "consumed_at")
    private Instant consumedAt;

    /**
     * Required by JPA.
     */
    protected SingleUseCodeEntity() {
    }

    /**
     * Mints a code.
     *
     * @param user      account the code grants access to
     * @param codeHash  hash of the generated code
     * @param purpose   what the code may be exchanged for
     * @param expiresAt moment after which the code is refused
     */
    public SingleUseCodeEntity(
            final UserEntity user,
            final String codeHash,
            final SingleUseCodePurpose purpose,
            final Instant expiresAt) {

        this.user = user;
        this.codeHash = codeHash;
        this.purpose = purpose;
        this.expiresAt = expiresAt;
    }

    /**
     * Returns the account the code grants access to.
     *
     * @return the account
     */
    public UserEntity getUser() {
        return user;
    }

    /**
     * Returns what this code may be exchanged for.
     *
     * @return the purpose
     */
    public SingleUseCodePurpose getPurpose() {
        return purpose;
    }

    /**
     * Returns the moment after which the code is refused.
     *
     * @return the expiry moment
     */
    public Instant getExpiresAt() {
        return expiresAt;
    }

    /**
     * Tells whether the code may still be redeemed.
     *
     * @param now moment to judge against
     * @return {@code true} when the code is neither spent nor expired
     */
    public boolean isRedeemableAt(final Instant now) {
        return consumedAt == null && now.isBefore(expiresAt);
    }

    /**
     * Marks the code as spent.
     *
     * @param consumedAt moment of redemption
     */
    public void markConsumed(final Instant consumedAt) {
        this.consumedAt = consumedAt;
    }

    /**
     * Returns the hash of the code.
     *
     * @return the stored hash
     */
    public String getCodeHash() {
        return codeHash;
    }
}
