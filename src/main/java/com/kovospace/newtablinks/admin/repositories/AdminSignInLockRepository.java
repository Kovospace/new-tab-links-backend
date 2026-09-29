package com.kovospace.newtablinks.admin.repositories;

import com.kovospace.newtablinks.admin.models.AdminSignInLockEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * The operator sign-in lockout, changed only by single atomic statements.
 *
 * <p>Every replica writes the same row, so nothing here reads a value and writes it back: two
 * pods counting a failure at the same moment would each read four and both write five. Each
 * change is one statement, and the row lock it takes makes the second pod's statement wait for
 * the first and then apply on top of it.</p>
 *
 * <p>Time is the database's {@code now()}, never a pod's clock, so replicas cannot disagree
 * about when a lock lifts.</p>
 *
 * @since 0.0.13
 */
public interface AdminSignInLockRepository extends JpaRepository<AdminSignInLockEntity, Short> {

    /**
     * Counts one failed sign-in, and locks sign-in when that makes too many.
     *
     * <p>An upsert, so the first failure creates the row and a row that has gone missing is
     * recreated rather than leaving the lockout switched off. {@code SET} expressions read the
     * row as it was before this statement, so the comparison is against the new count.</p>
     *
     * @param maximumConsecutiveFailures failures at which sign-in locks; positive
     * @param lockMilliseconds           how long the lock lasts from now
     * @return the number of rows written, always 1
     */
    @Modifying(clearAutomatically = true)
    @Query(value = "INSERT INTO admin_sign_in_lock AS sign_in_lock (id, consecutive_failures, locked_until) "
            + "VALUES (1, 1, CASE WHEN 1 >= :maximumConsecutiveFailures "
            + "        THEN now() + :lockMilliseconds * interval '1 millisecond' END) "
            + "ON CONFLICT (id) DO UPDATE SET "
            + "    consecutive_failures = sign_in_lock.consecutive_failures + 1, "
            + "    locked_until = CASE "
            + "        WHEN sign_in_lock.consecutive_failures + 1 >= :maximumConsecutiveFailures "
            + "        THEN now() + :lockMilliseconds * interval '1 millisecond' "
            + "        ELSE sign_in_lock.locked_until END",
            nativeQuery = true)
    int recordFailure(
            @Param("maximumConsecutiveFailures") int maximumConsecutiveFailures,
            @Param("lockMilliseconds") long lockMilliseconds);

    /**
     * Forgets a lock that has served its time, together with the failures that caused it.
     *
     * <p>Forgetting the failures with it is what makes the lock a delay rather than a permanent
     * lockout nobody can clear.</p>
     *
     * @return 1 when a lock had just expired, otherwise 0
     */
    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE admin_sign_in_lock SET consecutive_failures = 0, locked_until = NULL "
            + "WHERE id = 1 AND locked_until <= now()",
            nativeQuery = true)
    int releaseExpiredLock();

    /**
     * How long the current lock still has to run.
     *
     * @return the remaining milliseconds, rounded up; empty when sign-in is not locked
     */
    @Query(value = "SELECT CAST(CEIL(EXTRACT(EPOCH FROM (locked_until - now())) * 1000) AS bigint) "
            + "FROM admin_sign_in_lock WHERE id = 1 AND locked_until > now()",
            nativeQuery = true)
    Optional<Long> findRemainingLockMilliseconds();

    /**
     * Forgets every failure, because the operator has just proved who they are.
     *
     * @return 1 when there was a row to clear, otherwise 0
     */
    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE admin_sign_in_lock SET consecutive_failures = 0, locked_until = NULL "
            + "WHERE id = 1",
            nativeQuery = true)
    int clearFailures();
}
