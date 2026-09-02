package com.kovospace.newtablinks.auth.repositories;

import com.kovospace.newtablinks.auth.models.VisitorTokenEntity;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Persistence access for {@link VisitorTokenEntity}.
 *
 * @since 0.0.5
 */
@Repository
public interface VisitorTokenRepository extends JpaRepository<VisitorTokenEntity, UUID> {

    /**
     * Spends one use of a token, if and only if every rule still permits it.
     *
     * <p><strong>Deliberately one statement.</strong> Reading the row, judging it and advancing
     * its counter in three steps would let two requests that arrive together both read the same
     * count and both pass - which is precisely the concurrency an attacker would arrange on
     * purpose. Expressed as a single conditional update, the database decides, and exactly one of
     * the two wins.</p>
     *
     * <p>The row is left untouched when the update matches nothing, so a refused call costs a
     * caller nothing and cannot be used to burn somebody else's token.</p>
     *
     * @param tokenHash                hash of the token the caller presented
     * @param now                      moment being judged, recorded as the new last use
     * @param maximumUses              how many calls one token may make in total
     * @param latestIssueMomentAllowed newest issue moment that satisfies the first-use delay,
     *                                 that is {@code now - minimumFirstUseDelay}
     * @param latestPreviousUseAllowed newest previous use that satisfies the interval between
     *                                 calls, that is {@code now - minimumRequestInterval}
     * @return {@code 1} when the use was granted, {@code 0} when some rule refused it
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE VisitorTokenEntity visitorToken
            SET visitorToken.usageCount = visitorToken.usageCount + 1,
                visitorToken.lastUsedAt = :now,
                visitorToken.updatedAt = :now
            WHERE visitorToken.tokenHash = :tokenHash
              AND visitorToken.expiresAt > :now
              AND visitorToken.usageCount < :maximumUses
              AND visitorToken.createdAt <= :latestIssueMomentAllowed
              AND (visitorToken.lastUsedAt IS NULL
                   OR visitorToken.lastUsedAt <= :latestPreviousUseAllowed)
            """)
    int consumeOneUse(
            @Param("tokenHash") String tokenHash,
            @Param("now") Instant now,
            @Param("maximumUses") int maximumUses,
            @Param("latestIssueMomentAllowed") Instant latestIssueMomentAllowed,
            @Param("latestPreviousUseAllowed") Instant latestPreviousUseAllowed);

    /**
     * Finds a token by its hash.
     *
     * <p>Used only to explain a refusal after {@link #consumeOneUse} has already declined it.
     * Never to decide whether a call may proceed - that decision belongs to the conditional
     * update, which cannot be raced.</p>
     *
     * @param tokenHash hash of the token the caller presented
     * @return the matching token, or an empty optional when there is none
     */
    Optional<VisitorTokenEntity> findByTokenHash(String tokenHash);

    /**
     * Deletes every token whose lifetime has run out.
     *
     * <p>Written as a bulk statement rather than a derived {@code deleteBy...} query: these rows
     * arrive one per page load and are deleted by the thousand, and the derived form would load
     * every one of them into the persistence context first.</p>
     *
     * @param now moment to judge expiry against
     * @return how many rows were removed
     */
    @Modifying
    @Query("DELETE FROM VisitorTokenEntity visitorToken WHERE visitorToken.expiresAt <= :now")
    int deleteExpiredTokens(@Param("now") Instant now);
}
