package com.kovospace.newtablinks.statistics.repositories;

import com.kovospace.newtablinks.statistics.models.WebsiteVisitorHashEntity;
import com.kovospace.newtablinks.statistics.models.WebsiteVisitorHashKey;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Persistence of the hashes that let a website visitor be counted once per day.
 *
 * @since 0.0.11
 */
public interface WebsiteVisitorHashRepository
        extends JpaRepository<WebsiteVisitorHashEntity, WebsiteVisitorHashKey> {

    /**
     * Records a visitor for a day, unless they are already recorded.
     *
     * <p>The primary key decides, not a prior read: two requests from one visitor at the same
     * moment - on one pod or two - insert one row between them, and exactly one of them is told
     * so.</p>
     *
     * @param visitedOn   the day of the visit
     * @param visitorHash the visitor's hash for that day
     * @return 1 when this is the visitor's first visit that day, 0 when it is a repeat
     */
    @Modifying
    @Query(value = "INSERT INTO website_visitor_hash (day, visitor_hash) "
            + "VALUES (:visitedOn, :visitorHash) ON CONFLICT DO NOTHING",
            nativeQuery = true)
    int insertUnlessAlreadyRecorded(
            @Param("visitedOn") LocalDate visitedOn,
            @Param("visitorHash") byte[] visitorHash);

    /**
     * Deletes every hash of a day before the given one.
     *
     * @param firstDayToKeep the earliest day whose hashes survive, normally today
     * @return how many rows were deleted
     */
    @Modifying
    @Query(value = "DELETE FROM website_visitor_hash WHERE day < :firstDayToKeep",
            nativeQuery = true)
    int deleteRecordedBefore(@Param("firstDayToKeep") LocalDate firstDayToKeep);
}
