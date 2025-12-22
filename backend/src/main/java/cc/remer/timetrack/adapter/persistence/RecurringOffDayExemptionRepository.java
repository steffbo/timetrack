package cc.remer.timetrack.adapter.persistence;

import cc.remer.timetrack.domain.recurringoffday.RecurringOffDayExemption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository for RecurringOffDayExemption entities.
 */
@Repository
public interface RecurringOffDayExemptionRepository extends JpaRepository<RecurringOffDayExemption, Long> {

    /**
     * Find all exemptions for a specific recurring off-day rule.
     *
     * @param recurringOffDayId the recurring off-day ID
     * @return list of exemptions
     */
    List<RecurringOffDayExemption> findByRecurringOffDayId(Long recurringOffDayId);

    /**
     * Find all exemptions for a specific recurring off-day rule, ordered by date.
     *
     * @param recurringOffDayId the recurring off-day ID
     * @return list of exemptions ordered by date descending
     */
    List<RecurringOffDayExemption> findByRecurringOffDayIdOrderByExemptionDateDesc(Long recurringOffDayId);

    /**
     * Find an exemption for a specific recurring off-day on a specific date.
     *
     * @param recurringOffDayId the recurring off-day ID
     * @param exemptionDate the date to check
     * @return the exemption if found
     */
    Optional<RecurringOffDayExemption> findByRecurringOffDayIdAndExemptionDate(
            Long recurringOffDayId, LocalDate exemptionDate);

    /**
     * Check if an exemption exists for a specific recurring off-day on a specific date.
     *
     * @param recurringOffDayId the recurring off-day ID
     * @param exemptionDate the date to check
     * @return true if an exemption exists
     */
    boolean existsByRecurringOffDayIdAndExemptionDate(Long recurringOffDayId, LocalDate exemptionDate);

    /**
     * Find all exemptions for a user's recurring off-days on a specific date.
     * Useful for checking if any of a user's recurring off-days are exempted on a given date.
     *
     * @param userId the user ID
     * @param date the date to check
     * @return list of exemptions
     */
    @Query("SELECT e FROM RecurringOffDayExemption e " +
           "WHERE e.recurringOffDay.user.id = :userId " +
           "AND e.exemptionDate = :date")
    List<RecurringOffDayExemption> findByUserIdAndDate(
            @Param("userId") Long userId, @Param("date") LocalDate date);

    /**
     * Delete all exemptions for a recurring off-day.
     *
     * @param recurringOffDayId the recurring off-day ID
     */
    void deleteByRecurringOffDayId(Long recurringOffDayId);
}
