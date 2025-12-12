package cc.remer.timetrack.usecase.vacationbalance;

import cc.remer.timetrack.adapter.persistence.VacationBalanceRepository;
import cc.remer.timetrack.api.model.VacationBalanceResponse;
import cc.remer.timetrack.domain.vacationbalance.VacationBalance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Use case to get vacation balance for a user and year.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GetVacationBalance {

    private final VacationBalanceRepository vacationBalanceRepository;
    private final VacationBalanceMapper mapper;

    /**
     * Execute the use case to get vacation balance.
     *
     * @param userId the user ID
     * @param year the year (if null, uses current year)
     * @return the vacation balance response
     */
    public VacationBalanceResponse execute(Long userId, Integer year) {
        int targetYear = year != null ? year : java.time.Year.now().getValue();
        log.info("Getting vacation balance for user ID: {} and year: {}", userId, targetYear);

        VacationBalance balance = vacationBalanceRepository.findByUserIdAndYear(userId, targetYear)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Urlaubssaldo für Jahr " + targetYear + " nicht gefunden. Bitte kontaktieren Sie den Administrator."));

        return mapper.toResponse(balance);
    }
}
