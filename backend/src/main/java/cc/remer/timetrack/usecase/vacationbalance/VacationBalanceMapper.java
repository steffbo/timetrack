package cc.remer.timetrack.usecase.vacationbalance;

import cc.remer.timetrack.api.model.UpdateVacationBalanceRequest;
import cc.remer.timetrack.api.model.VacationBalanceResponse;
import cc.remer.timetrack.domain.vacationbalance.VacationBalance;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Mapper for VacationBalance entity and DTOs.
 */
@Component
public class VacationBalanceMapper {

    /**
     * Map entity to response DTO.
     */
    public VacationBalanceResponse toResponse(VacationBalance entity) {
        VacationBalanceResponse response = new VacationBalanceResponse();
        response.setId(entity.getId());
        response.setUserId(entity.getUser().getId());
        response.setYear(entity.getYear());
        response.setAnnualAllowanceDays(entity.getAnnualAllowanceDays().doubleValue());
        response.setCarriedOverDays(entity.getCarriedOverDays().doubleValue());
        response.setAdjustmentDays(entity.getAdjustmentDays().doubleValue());
        response.setUsedDays(entity.getUsedDays().doubleValue());
        response.setRemainingDays(entity.getRemainingDays().doubleValue());
        response.setCreatedAt(entity.getCreatedAt() != null ? entity.getCreatedAt().atOffset(java.time.ZoneOffset.UTC) : null);
        response.setUpdatedAt(entity.getUpdatedAt() != null ? entity.getUpdatedAt().atOffset(java.time.ZoneOffset.UTC) : null);
        return response;
    }

    /**
     * Map update request to entity fields.
     */
    public void mapUpdateRequest(UpdateVacationBalanceRequest request, VacationBalance entity) {
        if (request.getAnnualAllowanceDays() != null) {
            entity.setAnnualAllowanceDays(BigDecimal.valueOf(request.getAnnualAllowanceDays()));
        }
        if (request.getCarriedOverDays() != null) {
            entity.setCarriedOverDays(BigDecimal.valueOf(request.getCarriedOverDays()));
        }
        if (request.getAdjustmentDays() != null) {
            entity.setAdjustmentDays(BigDecimal.valueOf(request.getAdjustmentDays()));
        }

        // Recalculate remaining days
        entity.calculateRemainingDays();
    }
}
