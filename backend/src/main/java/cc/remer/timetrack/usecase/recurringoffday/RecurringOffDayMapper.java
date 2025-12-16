package cc.remer.timetrack.usecase.recurringoffday;

import cc.remer.timetrack.api.model.CreateRecurringOffDayRequest;
import cc.remer.timetrack.api.model.RecurringOffDayResponse;
import cc.remer.timetrack.api.model.UpdateRecurringOffDayRequest;
import cc.remer.timetrack.domain.recurringoffday.RecurringOffDay;
import cc.remer.timetrack.domain.recurringoffday.RecurrencePattern;
import cc.remer.timetrack.util.MapperUtils;
import org.springframework.stereotype.Component;

/**
 * Mapper for RecurringOffDay entity and DTOs.
 */
@Component
public class RecurringOffDayMapper {

    /**
     * Map entity to response DTO.
     */
    public RecurringOffDayResponse toResponse(RecurringOffDay entity) {
        RecurringOffDayResponse response = new RecurringOffDayResponse();
        response.setId(entity.getId());
        response.setUserId(entity.getUser().getId());
        response.setRecurrencePattern(
                RecurringOffDayResponse.RecurrencePatternEnum.fromValue(entity.getRecurrencePattern().name())
        );
        response.setWeekday(entity.getWeekday().intValue());
        response.setWeekInterval(entity.getWeekInterval());
        response.setReferenceDate(entity.getReferenceDate());
        response.setWeekOfMonth(entity.getWeekOfMonth() != null ? entity.getWeekOfMonth().intValue() : null);
        response.setStartDate(entity.getStartDate());
        response.setEndDate(entity.getEndDate());
        response.setIsActive(entity.getIsActive());
        response.setDescription(entity.getDescription());
        response.setCreatedAt(MapperUtils.toOffsetDateTime(entity.getCreatedAt()));
        response.setUpdatedAt(MapperUtils.toOffsetDateTime(entity.getUpdatedAt()));
        return response;
    }

    /**
     * Map create request to entity fields.
     */
    public void mapCreateRequest(CreateRecurringOffDayRequest request, RecurringOffDay entity) {
        entity.setRecurrencePattern(RecurrencePattern.valueOf(request.getRecurrencePattern().getValue()));
        entity.setWeekday(request.getWeekday().shortValue());
        entity.setWeekInterval(request.getWeekInterval());
        entity.setReferenceDate(request.getReferenceDate());
        entity.setWeekOfMonth(request.getWeekOfMonth() != null ? request.getWeekOfMonth().shortValue() : null);
        entity.setStartDate(request.getStartDate());
        entity.setEndDate(request.getEndDate());
        entity.setDescription(request.getDescription());
        entity.setIsActive(true); // New rules are active by default
    }

    /**
     * Map update request to entity fields.
     */
    public void mapUpdateRequest(UpdateRecurringOffDayRequest request, RecurringOffDay entity) {
        if (request.getRecurrencePattern() != null) {
            entity.setRecurrencePattern(RecurrencePattern.valueOf(request.getRecurrencePattern().getValue()));
        }
        if (request.getWeekday() != null) {
            entity.setWeekday(request.getWeekday().shortValue());
        }
        if (request.getWeekInterval() != null) {
            entity.setWeekInterval(request.getWeekInterval());
        }
        if (request.getReferenceDate() != null) {
            entity.setReferenceDate(request.getReferenceDate());
        }
        if (request.getWeekOfMonth() != null) {
            entity.setWeekOfMonth(request.getWeekOfMonth().shortValue());
        }
        if (request.getStartDate() != null) {
            entity.setStartDate(request.getStartDate());
        }
        if (request.getEndDate() != null) {
            entity.setEndDate(request.getEndDate());
        }
        if (request.getIsActive() != null) {
            entity.setIsActive(request.getIsActive());
        }
        if (request.getDescription() != null) {
            entity.setDescription(request.getDescription());
        }
    }
}
