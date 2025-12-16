package cc.remer.timetrack.usecase.recurringoffday;

import cc.remer.timetrack.adapter.persistence.RecurringOffDayRepository;
import cc.remer.timetrack.api.model.RecurringOffDayConflictWarningResponse;
import cc.remer.timetrack.domain.recurringoffday.RecurringOffDay;
import cc.remer.timetrack.domain.recurringoffday.RecurringOffDayConflictWarning;
import cc.remer.timetrack.util.MapperUtils;
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
        response.setAcknowledgedAt(MapperUtils.toOffsetDateTime(warning.getAcknowledgedAt()));
        response.setCreatedAt(MapperUtils.toOffsetDateTime(warning.getCreatedAt()));
        response.setUpdatedAt(MapperUtils.toOffsetDateTime(warning.getUpdatedAt()));

        return response;
    }
}
