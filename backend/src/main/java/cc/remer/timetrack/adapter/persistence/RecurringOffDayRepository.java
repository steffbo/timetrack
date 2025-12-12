package cc.remer.timetrack.adapter.persistence;

import cc.remer.timetrack.domain.recurringoffday.RecurringOffDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Repository for RecurringOffDay entities.
 */
@Repository
public interface RecurringOffDayRepository extends JpaRepository<RecurringOffDay, Long> {

    /**
     * Find all recurring off-days for a user.
     *
     * @param userId the user ID
     * @return list of recurring off-days
     */
    List<RecurringOffDay> findByUserId(Long userId);

    /**
     * Find active recurring off-days for a user.
     *
     * @param userId the user ID
     * @return list of active recurring off-days
     */
    List<RecurringOffDay> findByUserIdAndIsActiveTrue(Long userId);

    /**
     * Find active recurring off-days for a user that are applicable on a given date.
     *
     * @param userId the user ID
     * @param date the date to check
     * @return list of recurring off-days
     */
    @Query("SELECT r FROM RecurringOffDay r WHERE r.user.id = :userId " +
           "AND r.isActive = true " +
           "AND r.startDate <= :date " +
           "AND (r.endDate IS NULL OR r.endDate >= :date)")
    List<RecurringOffDay> findActiveByUserIdAndDate(@Param("userId") Long userId,
                                                      @Param("date") LocalDate date);
}
