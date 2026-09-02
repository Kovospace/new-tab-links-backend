package com.kovospace.newtablinks.auth.models;

import com.kovospace.newtablinks.common.models.AbstractAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A metered pass handed to an anonymous visitor when the website loads.
 *
 * <p>It grants nothing. It carries no identity, belongs to no account and unlocks no data - the
 * only thing it does is let its holder call the endpoints that disclose whether a username is
 * registered, at a pace and for a number of calls this row keeps count of.</p>
 *
 * <p>Only the hash of the token is stored, the same way every other bearer value in this service
 * is stored. The value is low-stakes, but a table of usable tokens is still a table nobody needs
 * to leak, and hashing costs one digest per call.</p>
 *
 * <p><strong>These rows are disposable and numerous</strong> - one per page load, of which a
 * visitor produces several - so nothing may ever be made to depend on one surviving. They are
 * deleted wholesale once expired by
 * {@link com.kovospace.newtablinks.auth.services.VisitorTokenCleanupScheduler}.</p>
 *
 * @since 0.0.5
 */
@Entity
@Table(name = "visitor_token")
public class VisitorTokenEntity extends AbstractAuditableEntity {

    /**
     * Hash of the token. The token itself exists only in the response that handed it out.
     */
    @Column(name = "token_hash", nullable = false, length = 100, unique = true)
    private String tokenHash;

    /**
     * Moment after which the token is refused and the row may be deleted.
     */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /**
     * How many guarded calls this token has already made.
     */
    @Column(name = "usage_count", nullable = false)
    private int usageCount;

    /**
     * Moment of the most recent guarded call, or {@code null} while the token is unused.
     *
     * <p>This is what the minimum interval between two calls is measured from. It is written by a
     * bulk update rather than by a setter, so that reading it, judging it and advancing it happen
     * as one statement - see
     * {@link com.kovospace.newtablinks.auth.repositories.VisitorTokenRepository#consumeOneUse}.</p>
     */
    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    /**
     * Required by JPA.
     */
    protected VisitorTokenEntity() {
    }

    /**
     * Issues a token.
     *
     * @param tokenHash hash of the generated token
     * @param expiresAt moment after which the token is refused
     */
    public VisitorTokenEntity(final String tokenHash, final Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.usageCount = 0;
    }

    /**
     * Returns the stored hash of the token.
     *
     * @return the hash
     */
    public String getTokenHash() {
        return tokenHash;
    }

    /**
     * Returns the moment after which the token is refused.
     *
     * @return the expiry moment
     */
    public Instant getExpiresAt() {
        return expiresAt;
    }

    /**
     * Returns how many guarded calls this token has made.
     *
     * @return the number of calls already spent
     */
    public int getUsageCount() {
        return usageCount;
    }

    /**
     * Returns when the token was last used.
     *
     * @return the moment of the last guarded call, or {@code null} when there was none
     */
    public Instant getLastUsedAt() {
        return lastUsedAt;
    }
}
