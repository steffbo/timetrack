package cc.remer.timetrack.usecase.workinghours;

import cc.remer.timetrack.adapter.persistence.WorkingHoursRepository;
import cc.remer.timetrack.api.model.UpdateWorkingDayConfig;
import cc.remer.timetrack.api.model.WorkingDayConfig;
import cc.remer.timetrack.domain.user.User;
import cc.remer.timetrack.domain.workinghours.WorkingHours;
import cc.remer.timetrack.usecase.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final WorkingHoursMapper responseMapper;
    private final WorkingDayUpdateMapper updateMapper;

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

        WorkingDayUpdate update = updateMapper.toSingleDayUpdate(weekday, dayConfig);

        User user = userService.getUserOrThrow(userId);

        WorkingHours workingHours = workingHoursRepository
                .findByUserIdAndWeekday(userId, weekday.shortValue())
                .orElse(null);

        workingHours = updateMapper.applyTo(workingHours, user, update);
        workingHours = workingHoursRepository.save(workingHours);

        log.info("Successfully updated working day {} for user ID: {}", weekday, userId);

        return responseMapper.toWorkingDayConfig(workingHours);
    }
}
