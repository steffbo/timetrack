package cc.remer.timetrack.usecase.timeoff;

import cc.remer.timetrack.adapter.persistence.TimeOffRepository;
import cc.remer.timetrack.api.model.CreateTimeOffRequest;
import cc.remer.timetrack.api.model.TimeOffResponse;
import cc.remer.timetrack.domain.timeoff.TimeOff;
import cc.remer.timetrack.domain.timeoff.TimeOffType;
import cc.remer.timetrack.domain.user.User;
import cc.remer.timetrack.usecase.user.UserService;
import cc.remer.timetrack.usecase.vacationbalance.VacationBalanceService;
import cc.remer.timetrack.util.ValidationUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use case to create a new time-off entry.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CreateTimeOff {

    private final TimeOffRepository timeOffRepository;
    private final UserService userService;
    private final TimeOffMapper mapper;
    private final VacationBalanceService vacationBalanceService;

    /**
     * Execute the use case to create a time-off entry.
     *
     * @param userId the user ID
     * @param request the create request
     * @return the created time-off response
     */
    @Transactional
    public TimeOffResponse execute(Long userId, CreateTimeOffRequest request) {
        log.info("Creating time-off entry for user ID: {}", userId);

        // Validate request
        validateRequest(request);

        // Find user
        User user = userService.getUserOrThrow(userId);

        // Create entity
        TimeOff entity = TimeOff.builder()
                .user(user)
                .build();

        // Map request to entity
        mapper.mapCreateRequest(request, entity);

        // Save
        TimeOff saved = timeOffRepository.save(entity);
        log.info("Created time-off entry with ID: {}", saved.getId());

        // Recalculate vacation balance if this is a vacation entry
        if (saved.getTimeOffType() == TimeOffType.VACATION) {
            vacationBalanceService.recalculateVacationBalanceForDateRange(
                    userId, saved.getStartDate(), saved.getEndDate());
        }

        return mapper.toResponse(saved);
    }

    /**
     * Validate the create request.
     */
    private void validateRequest(CreateTimeOffRequest request) {
        ValidationUtils.validateDateRange(request.getStartDate(), request.getEndDate());

        if (request.getHoursPerDay() != null && request.getHoursPerDay() < 0) {
            ValidationUtils.validateNonNegativeHours(request.getHoursPerDay());
        }
    }
}
