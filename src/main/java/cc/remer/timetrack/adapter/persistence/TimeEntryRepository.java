package cc.remer.timetrack.adapter.persistence;

import cc.remer.timetrack.domain.timeentry.EntryType;
import cc.remer.timetrack.domain.timeentry.TimeEntry;
import cc.remer.timetrack.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for TimeEntry entity operations.
 */
@Repository
public interface TimeEntryRepository extends JpaRepository<TimeEntry, Long> {

    /**
     * Find all time entries for a specific user.
     *
     * @param user the user
     * @return list of time entries
     */
    List<TimeEntry> findByUser(User user);

    /**
     * Find all time entries for a specific user ID.
     *
     * @param userId the user ID
     * @return list of time entries
     */
    List<TimeEntry> findByUserId(Long userId);

    /**
     * Find all time entries for a user within a date range.
     *
     * @param userId the user ID
     * @param startDate the start date (inclusive)
     * @param endDate the end date (inclusive)
     * @return list of time entries
     */
    List<TimeEntry> findByUserIdAndEntryDateBetween(Long userId, LocalDate startDate, LocalDate endDate);

    /**
     * Find all time entries for a user on a specific date.
     *
     * @param userId the user ID
     * @param entryDate the entry date
     * @return list of time entries
     */
    List<TimeEntry> findByUserIdAndEntryDate(Long userId, LocalDate entryDate);

    /**
     * Find the current active time entry for a user (not clocked out).
     *
     * @param userId the user ID
     * @return Optional containing the active time entry if found
     */
    Optional<TimeEntry> findByUserIdAndClockOutIsNull(Long userId);

    /**
     * Find all time entries of a specific type for a user.
     *
     * @param userId the user ID
     * @param entryType the entry type
     * @return list of time entries
     */
    List<TimeEntry> findByUserIdAndEntryType(Long userId, EntryType entryType);

    /**
     * Find all time entries for a user within a date range and of a specific type.
     *
     * @param userId the user ID
     * @param startDate the start date (inclusive)
     * @param endDate the end date (inclusive)
     * @param entryType the entry type
     * @return list of time entries
     */
    List<TimeEntry> findByUserIdAndEntryDateBetweenAndEntryType(
            Long userId,
            LocalDate startDate,
            LocalDate endDate,
            EntryType entryType
    );

    /**
     * Check if there are any overlapping time entries for a user.
     *
     * @param userId the user ID
     * @param clockIn the clock in time
     * @param clockOut the clock out time (can be null for active entries)
     * @param excludeId the ID to exclude from the check (for updates)
     * @return true if overlapping entries exist
     */
    @Query("SELECT CASE WHEN COUNT(te) > 0 THEN true ELSE false END FROM TimeEntry te " +
            "WHERE te.user.id = :userId " +
            "AND (:excludeId IS NULL OR te.id != :excludeId) " +
            "AND (" +
            "  (te.clockOut IS NULL) OR " +
            "  (:clockOut IS NULL) OR " +
            "  (te.clockIn < :clockOut AND (te.clockOut IS NULL OR te.clockOut > :clockIn))" +
            ")")
    boolean hasOverlappingEntries(
            @Param("userId") Long userId,
            @Param("clockIn") LocalDateTime clockIn,
            @Param("clockOut") LocalDateTime clockOut,
            @Param("excludeId") Long excludeId
    );

    /**
     * Calculate total hours worked for a user within a date range.
     *
     * @param userId the user ID
     * @param startDate the start date
     * @param endDate the end date
     * @return total hours worked (as minutes)
     */
    @Query("SELECT COALESCE(SUM(TIMESTAMPDIFF(MINUTE, te.clockIn, te.clockOut)), 0) " +
            "FROM TimeEntry te " +
            "WHERE te.user.id = :userId " +
            "AND te.entryDate BETWEEN :startDate AND :endDate " +
            "AND te.clockOut IS NOT NULL " +
            "AND te.entryType = 'WORK'")
    Long calculateTotalMinutesWorked(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}
