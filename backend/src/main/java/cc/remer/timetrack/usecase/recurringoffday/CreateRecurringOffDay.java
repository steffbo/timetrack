package cc.remer.timetrack.usecase.recurringoffday;

import cc.remer.timetrack.adapter.persistence.RecurringOffDayRepository;
import cc.remer.timetrack.adapter.persistence.UserRepository;
import cc.remer.timetrack.api.model.CreateRecurringOffDayRequest;
import cc.remer.timetrack.api.model.RecurringOffDayResponse;
import cc.remer.timetrack.domain.recurringoffday.RecurringOffDay;
import cc.remer.timetrack.domain.recurringoffday.RecurrencePattern;
import cc.remer.timetrack.domain.user.User;
import cc.remer.timetrack.exception.UserNotFoundException;
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
    private final UserRepository userRepository;
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
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found with ID: " + userId));

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
        if (request.getWeekday() < 1 || request.getWeekday() > 7) {
            throw new IllegalArgumentException("Wochentag muss zwischen 1 (Montag) und 7 (Sonntag) liegen");
        }

        // Validate pattern-specific fields
        RecurrencePattern pattern = RecurrencePattern.valueOf(request.getRecurrencePattern().getValue());
        switch (pattern) {
            case EVERY_NTH_WEEK:
                if (request.getWeekInterval() == null || request.getWeekInterval() < 1) {
                    throw new IllegalArgumentException("Wochenintervall ist erforderlich und muss mindestens 1 sein");
                }
                if (request.getReferenceDate() == null) {
                    throw new IllegalArgumentException("Referenzdatum ist für EVERY_NTH_WEEK erforderlich");
                }
                break;

            case NTH_WEEKDAY_OF_MONTH:
                if (request.getWeekOfMonth() == null || request.getWeekOfMonth() < 1 || request.getWeekOfMonth() > 5) {
                    throw new IllegalArgumentException("Woche des Monats muss zwischen 1 und 5 liegen");
                }
                break;
        }

        // Validate start date
        if (request.getStartDate() == null) {
            throw new IllegalArgumentException("Startdatum ist erforderlich");
        }

        // Validate end date (if provided)
        if (request.getEndDate() != null && request.getEndDate().isBefore(request.getStartDate())) {
            throw new IllegalArgumentException("Enddatum muss nach dem Startdatum liegen");
        }
    }
}
