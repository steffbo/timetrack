package cc.remer.timetrack.usecase.recurringoffday;

import cc.remer.timetrack.adapter.persistence.RecurringOffDayExemptionRepository;
import cc.remer.timetrack.adapter.persistence.RecurringOffDayRepository;
import cc.remer.timetrack.domain.recurringoffday.RecurringOffDay;
import cc.remer.timetrack.domain.recurringoffday.RecurringOffDayExemption;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use case to delete an exemption for a recurring off-day.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeleteRecurringOffDayExemption {

    private final RecurringOffDayRepository recurringOffDayRepository;
    private final RecurringOffDayExemptionRepository exemptionRepository;

    /**
     * Execute the use case to delete an exemption.
     *
     * @param userId the user ID (for authorization)
     * @param recurringOffDayId the recurring off-day ID
     * @param exemptionId the exemption ID
     * @throws IllegalArgumentException if the recurring off-day or exemption does not exist,
     *         or doesn't belong to the user
     */
    @Transactional
    public void execute(Long userId, Long recurringOffDayId, Long exemptionId) {
        log.info("Deleting exemption ID: {} for recurring off-day ID: {} for user: {}", 
                exemptionId, recurringOffDayId, userId);

        RecurringOffDay recurringOffDay = recurringOffDayRepository.findById(recurringOffDayId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Recurring off-day not found with ID: " + recurringOffDayId));

        // Check if the recurring off-day belongs to the user
        if (!recurringOffDay.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException(
                    "Recurring off-day does not belong to user: " + userId);
        }

        RecurringOffDayExemption exemption = exemptionRepository.findById(exemptionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Exemption not found with ID: " + exemptionId));

        // Check if the exemption belongs to the recurring off-day
        if (!exemption.getRecurringOffDay().getId().equals(recurringOffDayId)) {
            throw new IllegalArgumentException(
                    "Exemption does not belong to recurring off-day: " + recurringOffDayId);
        }

        exemptionRepository.delete(exemption);
        log.info("Deleted exemption with ID: {}", exemptionId);
    }
}
