package cc.remer.timetrack.usecase.recurringoffday;

import cc.remer.timetrack.adapter.persistence.RecurringOffDayExemptionRepository;
import cc.remer.timetrack.adapter.persistence.RecurringOffDayRepository;
import cc.remer.timetrack.api.model.CreateRecurringOffDayExemptionRequest;
import cc.remer.timetrack.api.model.RecurringOffDayExemptionResponse;
import cc.remer.timetrack.domain.recurringoffday.RecurringOffDay;
import cc.remer.timetrack.domain.recurringoffday.RecurringOffDayExemption;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Use case to create an exemption for a recurring off-day.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CreateRecurringOffDayExemption {

    private final RecurringOffDayRepository recurringOffDayRepository;
    private final RecurringOffDayExemptionRepository exemptionRepository;
    private final RecurringOffDayExemptionMapper mapper;
    private final RecurringOffDayEvaluator evaluator;

    /**
     * Execute the use case to create an exemption for a recurring off-day.
     *
     * @param userId the user ID (for authorization)
     * @param recurringOffDayId the recurring off-day ID
     * @param request the create request
     * @return the created exemption response
     * @throws IllegalArgumentException if the recurring off-day does not exist, doesn't belong to the user,
     *         the date doesn't match the pattern, or an exemption already exists
     */
    @Transactional
    public RecurringOffDayExemptionResponse execute(Long userId, Long recurringOffDayId, 
            CreateRecurringOffDayExemptionRequest request) {
        log.info("Creating exemption for recurring off-day ID: {} on date: {} for user: {}", 
                recurringOffDayId, request.getExemptionDate(), userId);

        RecurringOffDay recurringOffDay = recurringOffDayRepository.findById(recurringOffDayId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Recurring off-day not found with ID: " + recurringOffDayId));

        // Check if the recurring off-day belongs to the user
        if (!recurringOffDay.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException(
                    "Recurring off-day does not belong to user: " + userId);
        }

        LocalDate exemptionDate = request.getExemptionDate();

        // Validate that the date matches the recurring off-day pattern
        if (!evaluator.appliesToDate(recurringOffDay, exemptionDate)) {
            throw new IllegalArgumentException(
                    "Date " + exemptionDate + " does not match the recurring off-day pattern");
        }

        // Check if exemption already exists for this date
        if (exemptionRepository.existsByRecurringOffDayIdAndExemptionDate(recurringOffDayId, exemptionDate)) {
            throw new ExemptionAlreadyExistsException(
                    "Exemption already exists for date: " + exemptionDate);
        }

        RecurringOffDayExemption exemption = RecurringOffDayExemption.builder()
                .recurringOffDay(recurringOffDay)
                .exemptionDate(exemptionDate)
                .reason(request.getReason())
                .build();

        RecurringOffDayExemption savedExemption = exemptionRepository.save(exemption);
        log.info("Created exemption with ID: {}", savedExemption.getId());

        return mapper.toResponse(savedExemption);
    }

    /**
     * Exception thrown when an exemption already exists for a date.
     */
    public static class ExemptionAlreadyExistsException extends RuntimeException {
        public ExemptionAlreadyExistsException(String message) {
            super(message);
        }
    }
}
