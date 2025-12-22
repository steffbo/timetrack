package cc.remer.timetrack.usecase.recurringoffday;

import cc.remer.timetrack.api.model.RecurringOffDayExemptionResponse;
import cc.remer.timetrack.domain.recurringoffday.RecurringOffDayExemption;
import cc.remer.timetrack.util.MapperUtils;
import org.springframework.stereotype.Component;

/**
 * Mapper for RecurringOffDayExemption entity and DTOs.
 */
@Component
public class RecurringOffDayExemptionMapper {

    /**
     * Map entity to response DTO.
     */
    public RecurringOffDayExemptionResponse toResponse(RecurringOffDayExemption entity) {
        RecurringOffDayExemptionResponse response = new RecurringOffDayExemptionResponse();
        response.setId(entity.getId());
        response.setRecurringOffDayId(entity.getRecurringOffDay().getId());
        response.setExemptionDate(entity.getExemptionDate());
        response.setReason(entity.getReason());
        response.setCreatedAt(MapperUtils.toOffsetDateTime(entity.getCreatedAt()));
        return response;
    }
}
