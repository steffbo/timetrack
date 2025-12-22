package cc.remer.timetrack.usecase.recurringoffday;

import cc.remer.timetrack.adapter.persistence.RecurringOffDayExemptionRepository;
import cc.remer.timetrack.adapter.persistence.RecurringOffDayRepository;
import cc.remer.timetrack.api.model.RecurringOffDayExemptionResponse;
import cc.remer.timetrack.domain.recurringoffday.RecurringOffDay;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Use case to get all exemptions for a recurring off-day.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GetRecurringOffDayExemptions {

    private final RecurringOffDayRepository recurringOffDayRepository;
    private final RecurringOffDayExemptionRepository exemptionRepository;
    private final RecurringOffDayExemptionMapper mapper;

    /**
     * Execute the use case to get all exemptions for a recurring off-day.
     *
     * @param userId the user ID (for authorization)
     * @param recurringOffDayId the recurring off-day ID
     * @return list of exemption responses
     * @throws IllegalArgumentException if the recurring off-day does not exist or doesn't belong to the user
     */
    @Transactional(readOnly = true)
    public List<RecurringOffDayExemptionResponse> execute(Long userId, Long recurringOffDayId) {
        log.info("Getting exemptions for recurring off-day ID: {} for user: {}", recurringOffDayId, userId);

        RecurringOffDay recurringOffDay = recurringOffDayRepository.findById(recurringOffDayId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Recurring off-day not found with ID: " + recurringOffDayId));

        // Check if the recurring off-day belongs to the user
        if (!recurringOffDay.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException(
                    "Recurring off-day does not belong to user: " + userId);
        }

        return exemptionRepository.findByRecurringOffDayIdOrderByExemptionDateDesc(recurringOffDayId)
                .stream()
                .map(mapper::toResponse)
                .collect(Collectors.toList());
    }
}
