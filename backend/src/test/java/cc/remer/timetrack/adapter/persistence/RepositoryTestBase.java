package cc.remer.timetrack.adapter.persistence;

import cc.remer.timetrack.config.TestSecurityConfig;
import cc.remer.timetrack.domain.recurringoffday.RecurringOffDay;
import cc.remer.timetrack.domain.recurringoffday.RecurrencePattern;
import cc.remer.timetrack.domain.timeoff.TimeOff;
import cc.remer.timetrack.domain.timeoff.TimeOffType;
import cc.remer.timetrack.domain.user.GermanState;
import cc.remer.timetrack.domain.user.Role;
import cc.remer.timetrack.domain.user.User;
import cc.remer.timetrack.domain.vacationbalance.VacationBalance;
import cc.remer.timetrack.domain.workinghours.WorkingHours;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Base class for repository integration tests using Testcontainers.
 * Implements the "Shared Database Instance" pattern from Baeldung.
 *
 * The container is started once and shared across all test classes.
 * Flyway migrations run automatically via Spring Boot's auto-configuration.
 *
 * Provides centralized test fixture creation methods to reduce code duplication.
 *
 * @see <a href="https://www.baeldung.com/spring-boot-testcontainers-integration-test">Baeldung Testcontainers Guide</a>
 */
@SpringBootTest
@Import(TestSecurityConfig.class)
@Testcontainers
@Transactional
@ActiveProfiles("test")
public abstract class RepositoryTestBase {

    @Container
    protected static final TimetrackPostgresContainer postgres = TimetrackPostgresContainer.getInstance();

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected UserRepository userRepository;

    @Autowired(required = false)
    protected WorkingHoursRepository workingHoursRepository;

    @Autowired(required = false)
    protected RecurringOffDayRepository recurringOffDayRepository;

    @Autowired(required = false)
    protected TimeOffRepository timeOffRepository;

    @Autowired(required = false)
    protected VacationBalanceRepository vacationBalanceRepository;

    // ========== User Fixtures ==========

    /**
     * Create a test user with default settings.
     */
    protected User createTestUser(String email, String firstName, String lastName, Role role, GermanState state) {
        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode("password"))
                .firstName(firstName)
                .lastName(lastName)
                .role(role)
                .active(true)
                .state(state)
                .build();
        return userRepository.save(user);
    }

    /**
     * Create a regular test user with Berlin state.
     */
    protected User createTestUser() {
        return createTestUser("user@test.local", "Test", "User", Role.USER, GermanState.BERLIN);
    }

    /**
     * Create another test user for multi-user scenarios.
     */
    protected User createOtherTestUser() {
        return createTestUser("other@test.local", "Other", "User", Role.USER, GermanState.BERLIN);
    }

    /**
     * Create a test admin user.
     */
    protected User createTestAdmin() {
        return createTestUser("admin@test.local", "Admin", "User", Role.ADMIN, GermanState.BERLIN);
    }

    // ========== Working Hours Fixtures ==========

    /**
     * Create default working hours for a user (8h Mon-Fri, 0h Sat-Sun).
     */
    protected void createDefaultWorkingHours(User user) {
        if (workingHoursRepository == null) return;

        for (short weekday = 1; weekday <= 7; weekday++) {
            WorkingHours workingHours = WorkingHours.builder()
                    .user(user)
                    .weekday(weekday)
                    .hours(weekday <= 5 ? BigDecimal.valueOf(8.0) : BigDecimal.ZERO)
                    .isWorkingDay(weekday <= 5)
                    .build();
            workingHoursRepository.save(workingHours);
        }
    }

    // ========== Recurring Off-Day Fixtures ==========

    /**
     * Create a recurring off-day with EVERY_NTH_WEEK pattern.
     */
    protected RecurringOffDay createRecurringOffDay(User user, int weekday, int weekInterval,
                                                     LocalDate referenceDate, LocalDate startDate, String description) {
        if (recurringOffDayRepository == null) return null;

        RecurringOffDay entity = RecurringOffDay.builder()
                .user(user)
                .recurrencePattern(RecurrencePattern.EVERY_NTH_WEEK)
                .weekday((short) weekday)
                .weekInterval(weekInterval)
                .referenceDate(referenceDate)
                .startDate(startDate)
                .isActive(true)
                .description(description)
                .build();
        return recurringOffDayRepository.save(entity);
    }

    /**
     * Create a simple recurring off-day (every 4th Monday).
     */
    protected RecurringOffDay createRecurringOffDay(User user) {
        return createRecurringOffDay(
                user,
                1, // Monday
                4, // Every 4 weeks
                LocalDate.of(2025, 1, 6),
                LocalDate.of(2025, 1, 1),
                "Test recurring off-day"
        );
    }

    // ========== Time Off Fixtures ==========

    /**
     * Create a time-off entry.
     */
    protected TimeOff createTimeOff(User user, LocalDate startDate, LocalDate endDate,
                                    TimeOffType type, String notes) {
        if (timeOffRepository == null) return null;

        TimeOff entity = TimeOff.builder()
                .user(user)
                .startDate(startDate)
                .endDate(endDate)
                .timeOffType(type)
                .notes(notes)
                .build();
        return timeOffRepository.save(entity);
    }

    /**
     * Create a simple vacation time-off entry.
     */
    protected TimeOff createTimeOff(User user, LocalDate startDate, LocalDate endDate) {
        return createTimeOff(user, startDate, endDate, TimeOffType.VACATION, "Test time-off");
    }

    // ========== Vacation Balance Fixtures ==========

    /**
     * Create a vacation balance.
     */
    protected VacationBalance createVacationBalance(User user, int year, double annualAllowance,
                                                     double carriedOver, double adjustment, double used) {
        if (vacationBalanceRepository == null) return null;

        VacationBalance balance = VacationBalance.builder()
                .user(user)
                .year(year)
                .annualAllowanceDays(BigDecimal.valueOf(annualAllowance))
                .carriedOverDays(BigDecimal.valueOf(carriedOver))
                .adjustmentDays(BigDecimal.valueOf(adjustment))
                .usedDays(BigDecimal.valueOf(used))
                .build();
        balance.calculateRemainingDays();
        return vacationBalanceRepository.save(balance);
    }

    /**
     * Create a simple vacation balance for current year.
     */
    protected VacationBalance createVacationBalance(User user, int year) {
        return createVacationBalance(user, year, 30.0, 0.0, 0.0, 0.0);
    }
}
