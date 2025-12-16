package cc.remer.timetrack.usecase.timeoff;

import cc.remer.timetrack.adapter.persistence.TimeOffRepository;
import cc.remer.timetrack.api.model.TimeOffResponse;
import cc.remer.timetrack.api.model.UpdateTimeOffRequest;
import cc.remer.timetrack.domain.timeoff.TimeOff;
import cc.remer.timetrack.domain.timeoff.TimeOffType;
import cc.remer.timetrack.exception.TimeOffNotFoundException;
import cc.remer.timetrack.usecase.AuthorizationService;
import cc.remer.timetrack.usecase.vacationbalance.VacationBalanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Use case to update an existing time-off entry.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UpdateTimeOff {

    private final TimeOffRepository timeOffRepository;
    private final TimeOffMapper mapper;
    private final VacationBalanceService vacationBalanceService;
    private final AuthorizationService authorizationService;

    /**
     * Execute the use case to update a time-off entry.
     *
     * @param userId the user ID
     * @param id the time-off ID
     * @param request the update request
     * @return the updated time-off response
     */
    @Transactional
    public TimeOffResponse execute(Long userId, Long id, UpdateTimeOffRequest request) {
        log.info("Updating time-off entry ID: {} for user ID: {}", id, userId);

        // Find entity
        TimeOff entity = timeOffRepository.findById(id)
                .orElseThrow(() -> new TimeOffNotFoundException(id));

        // Check user owns this time-off entry
        authorizationService.validateOwnership(entity.getUser(), userId, "diesen Abwesenheitseintrag");

        // Store old values to determine which years to recalculate
        TimeOffType oldType = entity.getTimeOffType();
        int oldStartYear = entity.getStartDate().getYear();
        int oldEndYear = entity.getEndDate().getYear();

        // Map update request
        mapper.mapUpdateRequest(request, entity);

        // Save
        TimeOff updated = timeOffRepository.save(entity);
        log.info("Updated time-off entry ID: {}", updated.getId());

        // Recalculate vacation balance if this involves a vacation entry
        if (oldType == TimeOffType.VACATION || updated.getTimeOffType() == TimeOffType.VACATION) {
            // Recalculate for both old and new date ranges to handle all affected years
            LocalDate oldStartDate = LocalDate.of(oldStartYear, 1, 1);
            LocalDate oldEndDate = LocalDate.of(oldEndYear, 12, 31);
            LocalDate newStartDate = updated.getStartDate();
            LocalDate newEndDate = updated.getEndDate();

            // Get the full range from earliest to latest date
            LocalDate earliestDate = oldStartDate.isBefore(newStartDate) ? oldStartDate : newStartDate;
            LocalDate latestDate = oldEndDate.isAfter(newEndDate) ? oldEndDate : newEndDate;

            vacationBalanceService.recalculateVacationBalanceForDateRange(userId, earliestDate, latestDate);
        }

        return mapper.toResponse(updated);
    }
}
