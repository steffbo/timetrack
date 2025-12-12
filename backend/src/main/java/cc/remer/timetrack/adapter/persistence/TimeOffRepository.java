package cc.remer.timetrack.adapter.persistence;

import cc.remer.timetrack.domain.timeoff.TimeOff;
import cc.remer.timetrack.domain.timeoff.TimeOffType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Repository for TimeOff entities.
 */
@Repository
public interface TimeOffRepository extends JpaRepository<TimeOff, Long> {

    /**
     * Find all time off entries for a user.
     *
     * @param userId the user ID
     * @return list of time off entries
     */
    List<TimeOff> findByUserId(Long userId);

    /**
     * Find all time off entries for a user, ordered by start date descending.
     *
     * @param userId the user ID
     * @return list of time off entries
     */
    List<TimeOff> findByUserIdOrderByStartDateDesc(Long userId);

    /**
     * Find time off entries for a user within a date range.
     *
     * @param userId the user ID
     * @param startDate the start date
     * @param endDate the end date
     * @return list of time off entries
     */
    @Query("SELECT t FROM TimeOff t WHERE t.user.id = :userId " +
           "AND ((t.startDate <= :endDate AND t.endDate >= :startDate))")
    List<TimeOff> findByUserIdAndDateRange(@Param("userId") Long userId,
                                            @Param("startDate") LocalDate startDate,
                                            @Param("endDate") LocalDate endDate);

    /**
     * Find time off entries for a user by type and year.
     *
     * @param userId the user ID
     * @param timeOffType the time off type
     * @param yearStart the start of the year
     * @param yearEnd the end of the year
     * @return list of time off entries
     */
    @Query("SELECT t FROM TimeOff t WHERE t.user.id = :userId " +
           "AND t.timeOffType = :timeOffType " +
           "AND t.startDate >= :yearStart AND t.endDate <= :yearEnd")
    List<TimeOff> findByUserIdAndTypeAndYear(@Param("userId") Long userId,
                                              @Param("timeOffType") TimeOffType timeOffType,
                                              @Param("yearStart") LocalDate yearStart,
                                              @Param("yearEnd") LocalDate yearEnd);

    /**
     * Find time off entry that contains a specific date.
     *
     * @param userId the user ID
     * @param date the date
     * @return list of time off entries (could be multiple overlapping)
     */
    @Query("SELECT t FROM TimeOff t WHERE t.user.id = :userId " +
           "AND t.startDate <= :date AND t.endDate >= :date")
    List<TimeOff> findByUserIdAndDate(@Param("userId") Long userId,
                                       @Param("date") LocalDate date);
}
