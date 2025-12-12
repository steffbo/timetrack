package cc.remer.timetrack.usecase.vacationbalance;

import cc.remer.timetrack.adapter.persistence.UserRepository;
import cc.remer.timetrack.adapter.persistence.VacationBalanceRepository;
import cc.remer.timetrack.api.model.VacationBalanceResponse;
import cc.remer.timetrack.domain.user.User;
import cc.remer.timetrack.domain.vacationbalance.VacationBalance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Use case to get vacation balance for a user and year.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GetVacationBalance {

    private final VacationBalanceRepository vacationBalanceRepository;
    private final UserRepository userRepository;
    private final VacationBalanceMapper mapper;
    private final VacationBalanceService vacationBalanceService;

    private static final BigDecimal DEFAULT_ANNUAL_ALLOWANCE_DAYS = new BigDecimal("30.0");

    /**
     * Execute the use case to get vacation balance.
     * Always recalculates used days from time-off entries to ensure accuracy.
     *
     * @param userId the user ID
     * @param year the year (if null, uses current year)
     * @return the vacation balance response
     */
    public VacationBalanceResponse execute(Long userId, Integer year) {
        int targetYear = year != null ? year : java.time.Year.now().getValue();
        log.info("Getting vacation balance for user ID: {} and year: {}", userId, targetYear);

        // Get or create balance
        VacationBalance balance = vacationBalanceRepository.findByUserIdAndYear(userId, targetYear)
                .orElseGet(() -> createDefaultBalance(userId, targetYear));

        // Always recalculate used days from time-off entries to ensure accuracy
        // This handles both new and historical vacation entries
        vacationBalanceService.recalculateVacationBalance(userId, targetYear);

        // Reload the balance after recalculation
        balance = vacationBalanceRepository.findByUserIdAndYear(userId, targetYear)
                .orElseThrow(() -> new IllegalStateException("Balance should exist after recalculation"));

        return mapper.toResponse(balance);
    }

    /**
     * Create a default vacation balance for a user and year.
     *
     * @param userId the user ID
     * @param year the year
     * @return the created vacation balance
     */
    private VacationBalance createDefaultBalance(Long userId, int year) {
        log.info("Creating default vacation balance for user ID: {} and year: {} with {} days",
                userId, year, DEFAULT_ANNUAL_ALLOWANCE_DAYS);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));

        VacationBalance balance = VacationBalance.builder()
                .user(user)
                .year(year)
                .annualAllowanceDays(DEFAULT_ANNUAL_ALLOWANCE_DAYS)
                .carriedOverDays(BigDecimal.ZERO)
                .adjustmentDays(BigDecimal.ZERO)
                .usedDays(BigDecimal.ZERO)
                .build();

        balance.calculateRemainingDays();

        return vacationBalanceRepository.save(balance);
    }
}
