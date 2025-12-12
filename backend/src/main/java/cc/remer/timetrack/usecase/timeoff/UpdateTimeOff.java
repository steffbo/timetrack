package cc.remer.timetrack.usecase.timeoff;

import cc.remer.timetrack.adapter.persistence.TimeOffRepository;
import cc.remer.timetrack.api.model.TimeOffResponse;
import cc.remer.timetrack.api.model.UpdateTimeOffRequest;
import cc.remer.timetrack.domain.timeoff.TimeOff;
import cc.remer.timetrack.domain.timeoff.TimeOffType;
import cc.remer.timetrack.exception.ForbiddenException;
import cc.remer.timetrack.exception.TimeOffNotFoundException;
import cc.remer.timetrack.usecase.vacationbalance.VacationBalanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        if (!entity.getUser().getId().equals(userId)) {
            throw new ForbiddenException("Sie haben keine Berechtigung, diesen Abwesenheitseintrag zu ändern");
        }

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
            // Recalculate for all affected years (old and new)
            int newStartYear = updated.getStartDate().getYear();
            int newEndYear = updated.getEndDate().getYear();

            // Collect all unique years that need recalculation
            java.util.Set<Integer> yearsToRecalculate = new java.util.HashSet<>();
            yearsToRecalculate.add(oldStartYear);
            yearsToRecalculate.add(oldEndYear);
            yearsToRecalculate.add(newStartYear);
            yearsToRecalculate.add(newEndYear);

            for (Integer year : yearsToRecalculate) {
                vacationBalanceService.recalculateVacationBalance(userId, year);
            }
        }

        return mapper.toResponse(updated);
    }
}
