package cc.remer.timetrack.usecase.recurringoffday;

import cc.remer.timetrack.adapter.persistence.RecurringOffDayRepository;
import cc.remer.timetrack.api.model.CreateRecurringOffDayRequest;
import cc.remer.timetrack.api.model.RecurringOffDayResponse;
import cc.remer.timetrack.domain.recurringoffday.RecurringOffDay;
import cc.remer.timetrack.domain.recurringoffday.RecurrencePattern;
import cc.remer.timetrack.domain.user.User;
import cc.remer.timetrack.usecase.user.UserService;
import cc.remer.timetrack.util.ValidationUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use case to create a new recurring off-day rule.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CreateRecurringOffDay {

    private final RecurringOffDayRepository recurringOffDayRepository;
    private final UserService userService;
    private final RecurringOffDayMapper mapper;

    /**
     * Execute the use case to create a recurring off-day.
     *
     * @param userId the user ID
     * @param request the create request
     * @return the created recurring off-day response
     */
    @Transactional
    public RecurringOffDayResponse execute(Long userId, CreateRecurringOffDayRequest request) {
        log.info("Creating recurring off-day for user ID: {}", userId);

        // Validate request
        validateRequest(request);

        // Find user
        User user = userService.getUserOrThrow(userId);

        // Create entity
        RecurringOffDay entity = RecurringOffDay.builder()
                .user(user)
                .build();

        // Map request to entity
        mapper.mapCreateRequest(request, entity);

        // Save
        RecurringOffDay saved = recurringOffDayRepository.save(entity);
        log.info("Created recurring off-day with ID: {}", saved.getId());

        return mapper.toResponse(saved);
    }

    /**
     * Validate the create request.
     */
    private void validateRequest(CreateRecurringOffDayRequest request) {
        // Validate weekday
        ValidationUtils.validateWeekday(request.getWeekday());

        // Validate pattern-specific fields
        RecurrencePattern pattern = RecurrencePattern.valueOf(request.getRecurrencePattern().getValue());
        switch (pattern) {
            case EVERY_NTH_WEEK:
                ValidationUtils.validateWeekInterval(request.getWeekInterval());
                ValidationUtils.validateRequired(request.getReferenceDate(), "Referenzdatum");
                break;

            case NTH_WEEKDAY_OF_MONTH:
                ValidationUtils.validateWeekOfMonth(request.getWeekOfMonth());
                break;
        }

        // Validate start date and optional end date
        ValidationUtils.validateRequired(request.getStartDate(), "Startdatum");
        ValidationUtils.validateOptionalEndDate(request.getStartDate(), request.getEndDate(),
            "Enddatum muss nach dem Startdatum liegen");
    }
}
