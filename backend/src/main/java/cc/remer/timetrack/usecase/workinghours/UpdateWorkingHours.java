package cc.remer.timetrack.usecase.workinghours;

import cc.remer.timetrack.adapter.persistence.WorkingHoursRepository;
import cc.remer.timetrack.api.model.UpdateWorkingHoursRequest;
import cc.remer.timetrack.api.model.WorkingHoursResponse;
import cc.remer.timetrack.domain.user.User;
import cc.remer.timetrack.domain.workinghours.WorkingHours;
import cc.remer.timetrack.usecase.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
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
    private final WorkingHoursMapper responseMapper;
    private final WorkingDayUpdateMapper updateMapper;

    /**
     * Update working hours for the authenticated user.
     *
     * @param userId the authenticated user's ID
     * @param request the update working hours request
     * @return the updated working hours response
     */
    public WorkingHoursResponse execute(Long userId, UpdateWorkingHoursRequest request) {
        log.debug("Updating working hours for user ID: {}", userId);

        List<WorkingDayUpdate> updates = updateMapper.toFullWeekUpdates(request);

        User user = userService.getUserOrThrow(userId);

        List<WorkingHours> existingWorkingHours = workingHoursRepository.findByUserId(userId);
        Map<Short, WorkingHours> workingHoursMap = existingWorkingHours.stream()
                .collect(Collectors.toMap(WorkingHours::getWeekday, wh -> wh));

        for (WorkingDayUpdate update : updates) {
            WorkingHours workingHours = updateMapper.applyTo(workingHoursMap.get(update.weekday()), user, update);
            workingHoursRepository.save(workingHours);
        }

        // Retrieve updated working hours
        List<WorkingHours> updatedWorkingHours = workingHoursRepository.findByUserId(userId);

        log.info("Successfully updated working hours for user ID: {}", userId);

        return responseMapper.toResponse(userId, updatedWorkingHours);
    }
}
