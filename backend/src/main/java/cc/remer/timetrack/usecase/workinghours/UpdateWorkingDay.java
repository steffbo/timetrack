package cc.remer.timetrack.usecase.workinghours;

import cc.remer.timetrack.adapter.persistence.WorkingHoursRepository;
import cc.remer.timetrack.api.model.UpdateWorkingDayConfig;
import cc.remer.timetrack.api.model.WorkingDayConfig;
import cc.remer.timetrack.domain.user.User;
import cc.remer.timetrack.domain.workinghours.WorkingHours;
import cc.remer.timetrack.usecase.user.UserService;
import cc.remer.timetrack.util.ValidationUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

/**
 * Use case for updating a single working day configuration.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UpdateWorkingDay {

    private final WorkingHoursRepository workingHoursRepository;
    private final UserService userService;
    private final WorkingHoursMapper mapper;

    /**
     * Update working hours for a single weekday for the authenticated user.
     *
     * @param userId the authenticated user's ID
     * @param weekday the weekday to update (1=Monday, 7=Sunday)
     * @param dayConfig the update working day configuration
     * @return the updated working day configuration
     */
    public WorkingDayConfig execute(Long userId, Integer weekday, UpdateWorkingDayConfig dayConfig) {
        log.debug("Updating working day {} for user ID: {}", weekday, userId);

        // Validate weekday
        ValidationUtils.validateWeekday(weekday);

        // Validate request
        validateDayConfig(dayConfig);

        User user = userService.getUserOrThrow(userId);

        // Get existing working hours for this day
        WorkingHours workingHours = workingHoursRepository
                .findByUserIdAndWeekday(userId, weekday.shortValue())
                .orElse(null);

        // Parse optional time fields
        LocalTime startTime = dayConfig.getStartTime() != null ? LocalTime.parse(dayConfig.getStartTime()) : null;
        LocalTime endTime = dayConfig.getEndTime() != null ? LocalTime.parse(dayConfig.getEndTime()) : null;

        // Get break minutes (default to 0 if not provided)
        Integer breakMinutes = dayConfig.getBreakMinutes() != null ? dayConfig.getBreakMinutes() : 0;

        // Calculate hours from times if both are provided, otherwise use the hours field
        BigDecimal hours;
        if (startTime != null && endTime != null) {
            hours = calculateHoursFromTimes(startTime, endTime, breakMinutes);
        } else {
            hours = BigDecimal.valueOf(dayConfig.getHours());
        }

        if (workingHours == null) {
            // Create new working hours entry
            workingHours = WorkingHours.builder()
                    .user(user)
                    .weekday(weekday.shortValue())
                    .hours(hours)
                    .isWorkingDay(dayConfig.getIsWorkingDay())
                    .startTime(startTime)
                    .endTime(endTime)
                    .breakMinutes(breakMinutes)
                    .build();
        } else {
            // Update existing entry
            workingHours.setHours(hours);
            workingHours.setIsWorkingDay(dayConfig.getIsWorkingDay());
            workingHours.setStartTime(startTime);
            workingHours.setEndTime(endTime);
            workingHours.setBreakMinutes(breakMinutes);
        }

        workingHours = workingHoursRepository.save(workingHours);

        log.info("Successfully updated working day {} for user ID: {}", weekday, userId);

        return mapper.toWorkingDayConfig(workingHours);
    }

    /**
     * Calculate hours from start and end times, subtracting break minutes.
     *
     * @param startTime the start time
     * @param endTime the end time
     * @param breakMinutes the break duration in minutes
     * @return the net hours as BigDecimal (rounded to 2 decimal places)
     */
    private BigDecimal calculateHoursFromTimes(LocalTime startTime, LocalTime endTime, Integer breakMinutes) {
        long minutes = ChronoUnit.MINUTES.between(startTime, endTime);
        long netMinutes = minutes - (breakMinutes != null ? breakMinutes : 0);
        // Ensure non-negative
        netMinutes = Math.max(0, netMinutes);
        BigDecimal hours = BigDecimal.valueOf(netMinutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
        return hours;
    }

    /**
     * Validate the update working day configuration.
     *
     * @param dayConfig the day configuration
     * @throws IllegalArgumentException if validation fails
     */
    private void validateDayConfig(UpdateWorkingDayConfig dayConfig) {
        // Validate time fields consistency
        boolean hasStartTime = dayConfig.getStartTime() != null && !dayConfig.getStartTime().isEmpty();
        boolean hasEndTime = dayConfig.getEndTime() != null && !dayConfig.getEndTime().isEmpty();

        if (hasStartTime != hasEndTime) {
            throw new IllegalArgumentException("Start- und Endzeit müssen beide angegeben werden oder beide leer sein");
        }

        // Validate times if provided
        if (hasStartTime && hasEndTime) {
            try {
                LocalTime startTime = LocalTime.parse(dayConfig.getStartTime());
                LocalTime endTime = LocalTime.parse(dayConfig.getEndTime());

                ValidationUtils.validateTimeRange(startTime, endTime);
            } catch (Exception e) {
                throw new IllegalArgumentException("Ungültiges Zeitformat: " + e.getMessage());
            }
        }

        // Validate hours
        ValidationUtils.validateHours(dayConfig.getHours());

        // Validate break minutes
        if (dayConfig.getBreakMinutes() != null && dayConfig.getBreakMinutes() < 0) {
            throw new IllegalArgumentException("Pausenzeit darf nicht negativ sein");
        }

        if (dayConfig.getIsWorkingDay() == null) {
            throw new IllegalArgumentException("isWorkingDay darf nicht null sein");
        }
    }
}
