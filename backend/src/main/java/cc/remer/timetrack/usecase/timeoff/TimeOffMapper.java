package cc.remer.timetrack.usecase.timeoff;

import cc.remer.timetrack.api.model.CreateTimeOffRequest;
import cc.remer.timetrack.api.model.TimeOffResponse;
import cc.remer.timetrack.api.model.UpdateTimeOffRequest;
import cc.remer.timetrack.domain.timeoff.TimeOff;
import cc.remer.timetrack.domain.timeoff.TimeOffType;
import cc.remer.timetrack.domain.user.GermanState;
import cc.remer.timetrack.usecase.vacationbalance.WorkingDaysCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Mapper for TimeOff entity and DTOs.
 */
@Component
@RequiredArgsConstructor
public class TimeOffMapper {

    private final WorkingDaysCalculator workingDaysCalculator;

    /**
     * Map entity to response DTO.
     */
    public TimeOffResponse toResponse(TimeOff entity) {
        TimeOffResponse response = new TimeOffResponse();
        response.setId(entity.getId());
        response.setUserId(entity.getUser().getId());
        response.setStartDate(entity.getStartDate());
        response.setEndDate(entity.getEndDate());
        response.setTimeOffType(TimeOffResponse.TimeOffTypeEnum.fromValue(entity.getTimeOffType().name()));

        // Calculate working days (excludes weekends, holidays, recurring off-days)
        GermanState userState = entity.getUser().getState();
        int workingDays = workingDaysCalculator.calculateWorkingDays(
                entity.getUser().getId(),
                userState,
                entity.getStartDate(),
                entity.getEndDate()
        );
        response.setDays(workingDays);

        response.setHoursPerDay(entity.getHoursPerDay() != null ? entity.getHoursPerDay().doubleValue() : null);
        response.setNotes(entity.getNotes());
        response.setCreatedAt(entity.getCreatedAt() != null ? entity.getCreatedAt().atOffset(java.time.ZoneOffset.UTC) : null);
        response.setUpdatedAt(entity.getUpdatedAt() != null ? entity.getUpdatedAt().atOffset(java.time.ZoneOffset.UTC) : null);
        return response;
    }

    /**
     * Map create request to entity fields.
     */
    public void mapCreateRequest(CreateTimeOffRequest request, TimeOff entity) {
        entity.setStartDate(request.getStartDate());
        entity.setEndDate(request.getEndDate());
        entity.setTimeOffType(TimeOffType.valueOf(request.getTimeOffType().getValue()));
        entity.setHoursPerDay(request.getHoursPerDay() != null ? BigDecimal.valueOf(request.getHoursPerDay()) : null);
        entity.setNotes(request.getNotes());
    }

    /**
     * Map update request to entity fields.
     */
    public void mapUpdateRequest(UpdateTimeOffRequest request, TimeOff entity) {
        if (request.getStartDate() != null) {
            entity.setStartDate(request.getStartDate());
        }
        if (request.getEndDate() != null) {
            entity.setEndDate(request.getEndDate());
        }
        if (request.getTimeOffType() != null) {
            entity.setTimeOffType(TimeOffType.valueOf(request.getTimeOffType().getValue()));
        }
        if (request.getHoursPerDay() != null) {
            entity.setHoursPerDay(BigDecimal.valueOf(request.getHoursPerDay()));
        }
        if (request.getNotes() != null) {
            entity.setNotes(request.getNotes());
        }
    }
}
