package cc.remer.timetrack.usecase.workinghours;

import cc.remer.timetrack.adapter.persistence.WorkingHoursRepository;
import cc.remer.timetrack.api.model.UpdateWorkingDayConfig;
import cc.remer.timetrack.api.model.UpdateWorkingHoursRequest;
import cc.remer.timetrack.api.model.WorkingHoursResponse;
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
import java.util.*;
import java.util.stream.Collectors;

/**
 * Use case for updating working hours configuration.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UpdateWorkingHours {

    private final WorkingHoursRepository workingHoursRepository;
    private final UserService userService;
    private final WorkingHoursMapper mapper;

    /**
     * Update working hours for the authenticated user.
     *
     * @param userId the authenticated user's ID
     * @param request the update working hours request
     * @return the updated working hours response
     */
    public WorkingHoursResponse execute(Long userId, UpdateWorkingHoursRequest request) {
        log.debug("Updating working hours for user ID: {}", userId);

        // Validate request
        validateRequest(request);

        User user = userService.getUserOrThrow(userId);

        // Get existing working hours
        List<WorkingHours> existingWorkingHours = workingHoursRepository.findByUserId(userId);
        Map<Short, WorkingHours> workingHoursMap = existingWorkingHours.stream()
                .collect(Collectors.toMap(WorkingHours::getWeekday, wh -> wh));

        // Update or create working hours for each day
        for (UpdateWorkingDayConfig dayConfig : request.getWorkingDays()) {
            Short weekday = dayConfig.getWeekday().shortValue();
            Boolean isWorkingDay = dayConfig.getIsWorkingDay();

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

            WorkingHours workingHours = workingHoursMap.get(weekday);
            if (workingHours == null) {
                // Create new working hours entry
                workingHours = WorkingHours.builder()
                        .user(user)
                        .weekday(weekday)
                        .hours(hours)
                        .isWorkingDay(isWorkingDay)
                        .startTime(startTime)
                        .endTime(endTime)
                        .breakMinutes(breakMinutes)
                        .build();
            } else {
                // Update existing entry
                workingHours.setHours(hours);
                workingHours.setIsWorkingDay(isWorkingDay);
                workingHours.setStartTime(startTime);
                workingHours.setEndTime(endTime);
                workingHours.setBreakMinutes(breakMinutes);
            }

            workingHoursRepository.save(workingHours);
        }

        // Retrieve updated working hours
        List<WorkingHours> updatedWorkingHours = workingHoursRepository.findByUserId(userId);

        log.info("Successfully updated working hours for user ID: {}", userId);

        return mapper.toResponse(userId, updatedWorkingHours);
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
     * Validate the update working hours request.
     *
     * @param request the update request
     * @throws IllegalArgumentException if validation fails
     */
    private void validateRequest(UpdateWorkingHoursRequest request) {
        if (request.getWorkingDays() == null || request.getWorkingDays().isEmpty()) {
            throw new IllegalArgumentException("Arbeitsstunden-Konfiguration darf nicht leer sein");
        }

        if (request.getWorkingDays().size() != 7) {
            throw new IllegalArgumentException("Es müssen genau 7 Wochentage konfiguriert werden");
        }

        // Validate each day
        Set<Integer> weekdaysSeen = new HashSet<>();
        for (UpdateWorkingDayConfig dayConfig : request.getWorkingDays()) {
            // Validate weekday
            ValidationUtils.validateWeekday(dayConfig.getWeekday());

            if (weekdaysSeen.contains(dayConfig.getWeekday())) {
                throw new IllegalArgumentException("Doppelter Wochentag: " + dayConfig.getWeekday());
            }
            weekdaysSeen.add(dayConfig.getWeekday());

            // Validate time fields consistency
            boolean hasStartTime = dayConfig.getStartTime() != null && !dayConfig.getStartTime().isEmpty();
            boolean hasEndTime = dayConfig.getEndTime() != null && !dayConfig.getEndTime().isEmpty();

            if (hasStartTime != hasEndTime) {
                throw new IllegalArgumentException("Start- und Endzeit müssen beide angegeben werden oder beide leer sein für Wochentag " + dayConfig.getWeekday());
            }

            // Validate times if provided
            if (hasStartTime && hasEndTime) {
                try {
                    LocalTime startTime = LocalTime.parse(dayConfig.getStartTime());
                    LocalTime endTime = LocalTime.parse(dayConfig.getEndTime());

                    ValidationUtils.validateTimeRange(startTime, endTime);
                } catch (Exception e) {
                    throw new IllegalArgumentException("Ungültiges Zeitformat für Wochentag " + dayConfig.getWeekday() + ": " + e.getMessage());
                }
            }

            // Validate hours
            ValidationUtils.validateHours(dayConfig.getHours());

            // Validate break minutes
            if (dayConfig.getBreakMinutes() != null && dayConfig.getBreakMinutes() < 0) {
                throw new IllegalArgumentException("Pausenzeit darf nicht negativ sein für Wochentag " + dayConfig.getWeekday());
            }

            if (dayConfig.getIsWorkingDay() == null) {
                throw new IllegalArgumentException("isWorkingDay darf nicht null sein für Wochentag " + dayConfig.getWeekday());
            }
        }

        // Ensure all weekdays 1-7 are present
        for (int i = 1; i <= 7; i++) {
            if (!weekdaysSeen.contains(i)) {
                throw new IllegalArgumentException("Fehlender Wochentag: " + i);
            }
        }
    }
}
