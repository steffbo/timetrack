package cc.remer.timetrack.adapter.persistence;

import cc.remer.timetrack.domain.recurringoffday.RecurringOffDayConflictWarning;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository for RecurringOffDayConflictWarning entities.
 */
@Repository
public interface RecurringOffDayConflictWarningRepository extends JpaRepository<RecurringOffDayConflictWarning, Long> {

    /**
     * Find all warnings for a user.
     *
     * @param userId the user ID
     * @return list of warnings
     */
    List<RecurringOffDayConflictWarning> findByUserIdOrderByConflictDateDesc(Long userId);

    /**
     * Find unacknowledged warnings for a user.
     *
     * @param userId the user ID
     * @return list of unacknowledged warnings
     */
    List<RecurringOffDayConflictWarning> findByUserIdAndAcknowledgedFalseOrderByConflictDateDesc(Long userId);

    /**
     * Find warnings for a user within a date range.
     *
     * @param userId the user ID
     * @param startDate start of date range
     * @param endDate end of date range
     * @return list of warnings in the range
     */
    @Query("SELECT w FROM RecurringOffDayConflictWarning w WHERE w.user.id = :userId " +
           "AND w.conflictDate >= :startDate AND w.conflictDate <= :endDate " +
           "ORDER BY w.conflictDate DESC")
    List<RecurringOffDayConflictWarning> findByUserIdAndDateRange(@Param("userId") Long userId,
                                                                    @Param("startDate") LocalDate startDate,
                                                                    @Param("endDate") LocalDate endDate);

    /**
     * Find warning for a specific user and date.
     *
     * @param userId the user ID
     * @param conflictDate the conflict date
     * @return optional warning
     */
    Optional<RecurringOffDayConflictWarning> findByUserIdAndConflictDate(Long userId, LocalDate conflictDate);

    /**
     * Check if a warning exists for a specific date and user.
     *
     * @param userId the user ID
     * @param conflictDate the conflict date
     * @return true if exists
     */
    boolean existsByUserIdAndConflictDate(Long userId, LocalDate conflictDate);

    /**
     * Find warnings for a specific time entry.
     *
     * @param timeEntryId the time entry ID
     * @return list of warnings for this time entry
     */
    @Query("SELECT w FROM RecurringOffDayConflictWarning w WHERE w.timeEntryId = :timeEntryId")
    List<RecurringOffDayConflictWarning> findByTimeEntryId(@Param("timeEntryId") Long timeEntryId);

    /**
     * Delete warnings for a specific time entry.
     *
     * @param timeEntryId the time entry ID
     */
    @Modifying
    @Query("DELETE FROM RecurringOffDayConflictWarning w WHERE w.timeEntryId = :timeEntryId")
    void deleteByTimeEntryId(@Param("timeEntryId") Long timeEntryId);
}
