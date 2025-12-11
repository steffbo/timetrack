package cc.remer.timetrack.usecase.workinghours;

import cc.remer.timetrack.adapter.persistence.UserRepository;
import cc.remer.timetrack.adapter.persistence.WorkingHoursRepository;
import cc.remer.timetrack.api.model.UpdateWorkingDayConfig;
import cc.remer.timetrack.api.model.UpdateWorkingHoursRequest;
import cc.remer.timetrack.api.model.WorkingHoursResponse;
import cc.remer.timetrack.domain.user.User;
import cc.remer.timetrack.domain.workinghours.WorkingHours;
import cc.remer.timetrack.exception.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
    private final UserRepository userRepository;
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

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("Benutzer nicht gefunden"));

        // Get existing working hours
        List<WorkingHours> existingWorkingHours = workingHoursRepository.findByUserId(userId);
        Map<Short, WorkingHours> workingHoursMap = existingWorkingHours.stream()
                .collect(Collectors.toMap(WorkingHours::getWeekday, wh -> wh));

        // Update or create working hours for each day
        for (UpdateWorkingDayConfig dayConfig : request.getWorkingDays()) {
            Short weekday = dayConfig.getWeekday().shortValue();
            BigDecimal hours = BigDecimal.valueOf(dayConfig.getHours());
            Boolean isWorkingDay = dayConfig.getIsWorkingDay();

            WorkingHours workingHours = workingHoursMap.get(weekday);
            if (workingHours == null) {
                // Create new working hours entry
                workingHours = WorkingHours.builder()
                        .user(user)
                        .weekday(weekday)
                        .hours(hours)
                        .isWorkingDay(isWorkingDay)
                        .build();
            } else {
                // Update existing entry
                workingHours.setHours(hours);
                workingHours.setIsWorkingDay(isWorkingDay);
            }

            workingHoursRepository.save(workingHours);
        }

        // Retrieve updated working hours
        List<WorkingHours> updatedWorkingHours = workingHoursRepository.findByUserId(userId);

        log.info("Successfully updated working hours for user ID: {}", userId);

        return mapper.toResponse(userId, updatedWorkingHours);
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
            if (dayConfig.getWeekday() == null || dayConfig.getWeekday() < 1 || dayConfig.getWeekday() > 7) {
                throw new IllegalArgumentException("Ungültiger Wochentag: " + dayConfig.getWeekday());
            }

            if (weekdaysSeen.contains(dayConfig.getWeekday())) {
                throw new IllegalArgumentException("Doppelter Wochentag: " + dayConfig.getWeekday());
            }
            weekdaysSeen.add(dayConfig.getWeekday());

            if (dayConfig.getHours() == null || dayConfig.getHours() < 0 || dayConfig.getHours() > 24) {
                throw new IllegalArgumentException("Ungültige Stundenanzahl für Wochentag " + dayConfig.getWeekday() + ": " + dayConfig.getHours());
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
