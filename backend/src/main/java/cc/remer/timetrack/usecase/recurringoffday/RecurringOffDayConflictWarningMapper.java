package cc.remer.timetrack.usecase.recurringoffday;

import cc.remer.timetrack.adapter.persistence.RecurringOffDayRepository;
import cc.remer.timetrack.api.model.RecurringOffDayConflictWarningResponse;
import cc.remer.timetrack.domain.recurringoffday.RecurringOffDay;
import cc.remer.timetrack.domain.recurringoffday.RecurringOffDayConflictWarning;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;


/**
 * Mapper for RecurringOffDayConflictWarning domain to API model.
 */
@Component
@RequiredArgsConstructor
public class RecurringOffDayConflictWarningMapper {

    private final RecurringOffDayRepository recurringOffDayRepository;

    /**
     * Map domain warning to API response model.
     *
     * @param warning the domain warning
     * @return the API response model
     */
    public RecurringOffDayConflictWarningResponse toResponse(RecurringOffDayConflictWarning warning) {
        if (warning == null) {
            return null;
        }

        RecurringOffDayConflictWarningResponse response = new RecurringOffDayConflictWarningResponse();
        response.setId(warning.getId());
        response.setUserId(warning.getUser().getId());
        response.setConflictDate(warning.getConflictDate());
        response.setTimeEntryId(warning.getTimeEntryId());
        response.setRecurringOffDayId(warning.getRecurringOffDayId());

        // Fetch recurring off-day description if available
        if (warning.getRecurringOffDayId() != null) {
            recurringOffDayRepository.findById(warning.getRecurringOffDayId())
                    .ifPresent(offDay -> response.setRecurringOffDayDescription(offDay.getDescription()));
        }

        response.setAcknowledged(warning.getAcknowledged());
        response.setAcknowledgedAt(warning.getAcknowledgedAt() != null
                ? warning.getAcknowledgedAt().atOffset(java.time.ZoneOffset.UTC)
                : null);
        response.setCreatedAt(warning.getCreatedAt() != null
                ? warning.getCreatedAt().atOffset(java.time.ZoneOffset.UTC)
                : null);
        response.setUpdatedAt(warning.getUpdatedAt() != null
                ? warning.getUpdatedAt().atOffset(java.time.ZoneOffset.UTC)
                : null);

        return response;
    }
}
