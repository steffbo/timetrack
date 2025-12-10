package cc.remer.timetrack.adapter.persistence;

import cc.remer.timetrack.domain.user.User;
import cc.remer.timetrack.domain.workinghours.WorkingHours;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for WorkingHours entity operations.
 */
@Repository
public interface WorkingHoursRepository extends JpaRepository<WorkingHours, Long> {

    /**
     * Find all working hours configurations for a specific user.
     *
     * @param user the user
     * @return list of working hours for the user
     */
    List<WorkingHours> findByUser(User user);

    /**
     * Find all working hours configurations for a specific user ID.
     *
     * @param userId the user ID
     * @return list of working hours for the user
     */
    List<WorkingHours> findByUserId(Long userId);

    /**
     * Find working hours for a specific user and weekday.
     *
     * @param user the user
     * @param weekday the weekday (1=Monday, 7=Sunday)
     * @return Optional containing the working hours if found
     */
    Optional<WorkingHours> findByUserAndWeekday(User user, Short weekday);

    /**
     * Find working hours for a specific user ID and weekday.
     *
     * @param userId the user ID
     * @param weekday the weekday (1=Monday, 7=Sunday)
     * @return Optional containing the working hours if found
     */
    Optional<WorkingHours> findByUserIdAndWeekday(Long userId, Short weekday);

    /**
     * Delete all working hours for a specific user.
     *
     * @param user the user
     */
    void deleteByUser(User user);

    /**
     * Delete all working hours for a specific user ID.
     *
     * @param userId the user ID
     */
    void deleteByUserId(Long userId);

    /**
     * Check if working hours exist for a user and weekday.
     *
     * @param userId the user ID
     * @param weekday the weekday
     * @return true if working hours exist
     */
    boolean existsByUserIdAndWeekday(Long userId, Short weekday);
}
