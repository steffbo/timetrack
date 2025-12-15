package cc.remer.timetrack.usecase.vacationbalance;

import cc.remer.timetrack.adapter.persistence.RecurringOffDayRepository;
import cc.remer.timetrack.adapter.persistence.TimeOffRepository;
import cc.remer.timetrack.adapter.persistence.UserRepository;
import cc.remer.timetrack.adapter.persistence.WorkingHoursRepository;
import cc.remer.timetrack.domain.publicholiday.GermanPublicHolidays;
import cc.remer.timetrack.domain.user.GermanState;
import cc.remer.timetrack.domain.user.Role;
import cc.remer.timetrack.domain.user.User;
import cc.remer.timetrack.domain.workinghours.WorkingHours;
import cc.remer.timetrack.usecase.recurringoffday.RecurringOffDayEvaluator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for WorkingDaysCalculator, focusing on half-day holidays feature.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Working Days Calculator - Half-Day Holidays Tests")
class WorkingDaysCalculatorTest {

    @Mock
    private WorkingHoursRepository workingHoursRepository;

    @Mock
    private RecurringOffDayRepository recurringOffDayRepository;

    @Mock
    private TimeOffRepository timeOffRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private GermanPublicHolidays germanPublicHolidays;

    @Mock
    private RecurringOffDayEvaluator recurringOffDayEvaluator;

    private WorkingDaysCalculator calculator;

    private User userWithHalfDayHolidays;
    private User userWithoutHalfDayHolidays;
    private List<WorkingHours> standardWorkingHours;

    @BeforeEach
    void setUp() {
        calculator = new WorkingDaysCalculator(
                workingHoursRepository,
                recurringOffDayRepository,
                timeOffRepository,
                userRepository,
                germanPublicHolidays,
                recurringOffDayEvaluator
        );

        // Create user with half-day holidays enabled
        userWithHalfDayHolidays = User.builder()
                .id(1L)
                .email("user@test.com")
                .firstName("Test")
                .lastName("User")
                .passwordHash("hash")
                .role(Role.USER)
                .active(true)
                .state(GermanState.BERLIN)
                .halfDayHolidaysEnabled(true)
                .build();

        // Create user without half-day holidays
        userWithoutHalfDayHolidays = User.builder()
                .id(2L)
                .email("user2@test.com")
                .firstName("Test")
                .lastName("User2")
                .passwordHash("hash")
                .role(Role.USER)
                .active(true)
                .state(GermanState.BERLIN)
                .halfDayHolidaysEnabled(false)
                .build();

        // Standard Mon-Fri working hours
        standardWorkingHours = new ArrayList<>();
        for (short weekday = 1; weekday <= 7; weekday++) {
            boolean isWorkingDay = weekday >= 1 && weekday <= 5; // Mon-Fri
            WorkingHours wh = WorkingHours.builder()
                    .weekday(weekday)
                    .isWorkingDay(isWorkingDay)
                    .hours(isWorkingDay ? new BigDecimal("8.00") : BigDecimal.ZERO)
                    .build();
            standardWorkingHours.add(wh);
        }

        // Default mock behavior (lenient to avoid unnecessary stubbing errors)
        lenient().when(recurringOffDayRepository.findByUserId(anyLong())).thenReturn(List.of());
        lenient().when(timeOffRepository.findByUserIdAndDateRange(anyLong(), any(), any())).thenReturn(List.of());
        lenient().when(germanPublicHolidays.isPublicHoliday(any(), any())).thenReturn(false);
    }

    @Test
    @DisplayName("Dec 24 counts as 0.5 days when half-day holidays enabled")
    void testDecember24AsHalfDay() {
        // Given: User with half-day holidays enabled, vacation Dec 23-27, 2025
        // Dec 23 (Tue) = 1.0, Dec 24 (Wed) = 0.5, Dec 25 (Thu) = 0 (Christmas), Dec 26 (Fri) = 0 (2nd Christmas Day), Dec 27 (Sat) = 0 (weekend)
        when(userRepository.findById(1L)).thenReturn(Optional.of(userWithHalfDayHolidays));
        when(workingHoursRepository.findByUserId(1L)).thenReturn(standardWorkingHours);
        when(germanPublicHolidays.isPublicHoliday(LocalDate.of(2025, 12, 25), GermanState.BERLIN)).thenReturn(true); // Christmas
        when(germanPublicHolidays.isPublicHoliday(LocalDate.of(2025, 12, 26), GermanState.BERLIN)).thenReturn(true); // 2nd Christmas Day

        // When
        BigDecimal result = calculator.calculateWorkingDays(
                1L,
                GermanState.BERLIN,
                LocalDate.of(2025, 12, 23),
                LocalDate.of(2025, 12, 27)
        );

        // Then: Dec 23 (1.0) + Dec 24 (0.5) = 1.5 days
        assertThat(result).isEqualByComparingTo(new BigDecimal("1.5"));
    }

    @Test
    @DisplayName("Dec 31 counts as 0.5 days when half-day holidays enabled")
    void testDecember31AsHalfDay() {
        // Given: User with half-day holidays enabled, vacation Dec 30, 2025 - Jan 2, 2026
        // Dec 30 (Tue) = 1.0, Dec 31 (Wed) = 0.5, Jan 1 (Thu) = 0 (New Year), Jan 2 (Fri) = 1.0
        when(userRepository.findById(1L)).thenReturn(Optional.of(userWithHalfDayHolidays));
        when(workingHoursRepository.findByUserId(1L)).thenReturn(standardWorkingHours);
        when(germanPublicHolidays.isPublicHoliday(LocalDate.of(2026, 1, 1), GermanState.BERLIN)).thenReturn(true); // New Year

        // When
        BigDecimal result = calculator.calculateWorkingDays(
                1L,
                GermanState.BERLIN,
                LocalDate.of(2025, 12, 30),
                LocalDate.of(2026, 1, 2)
        );

        // Then: Dec 30 (1.0) + Dec 31 (0.5) + Jan 2 (1.0) = 2.5 days
        assertThat(result).isEqualByComparingTo(new BigDecimal("2.5"));
    }

    @Test
    @DisplayName("Taking only Dec 24 counts as 0.5 days")
    void testOnlyDecember24() {
        // Given: User with half-day holidays enabled, vacation only on Dec 24, 2025 (Wednesday)
        when(userRepository.findById(1L)).thenReturn(Optional.of(userWithHalfDayHolidays));
        when(workingHoursRepository.findByUserId(1L)).thenReturn(standardWorkingHours);

        // When
        BigDecimal result = calculator.calculateWorkingDays(
                1L,
                GermanState.BERLIN,
                LocalDate.of(2025, 12, 24),
                LocalDate.of(2025, 12, 24)
        );

        // Then: Dec 24 = 0.5 days
        assertThat(result).isEqualByComparingTo(new BigDecimal("0.5"));
    }

    @Test
    @DisplayName("Taking only Dec 31 counts as 0.5 days")
    void testOnlyDecember31() {
        // Given: User with half-day holidays enabled, vacation only on Dec 31, 2025 (Wednesday)
        when(userRepository.findById(1L)).thenReturn(Optional.of(userWithHalfDayHolidays));
        when(workingHoursRepository.findByUserId(1L)).thenReturn(standardWorkingHours);

        // When
        BigDecimal result = calculator.calculateWorkingDays(
                1L,
                GermanState.BERLIN,
                LocalDate.of(2025, 12, 31),
                LocalDate.of(2025, 12, 31)
        );

        // Then: Dec 31 = 0.5 days
        assertThat(result).isEqualByComparingTo(new BigDecimal("0.5"));
    }

    @Test
    @DisplayName("Dec 24 and 31 count as full days when feature disabled")
    void testHalfDayHolidaysDisabled() {
        // Given: User WITHOUT half-day holidays enabled, vacation Dec 23-27, 2025
        when(userRepository.findById(2L)).thenReturn(Optional.of(userWithoutHalfDayHolidays));
        when(workingHoursRepository.findByUserId(2L)).thenReturn(standardWorkingHours);
        when(germanPublicHolidays.isPublicHoliday(LocalDate.of(2025, 12, 25), GermanState.BERLIN)).thenReturn(true);
        when(germanPublicHolidays.isPublicHoliday(LocalDate.of(2025, 12, 26), GermanState.BERLIN)).thenReturn(true);

        // When
        BigDecimal result = calculator.calculateWorkingDays(
                2L,
                GermanState.BERLIN,
                LocalDate.of(2025, 12, 23),
                LocalDate.of(2025, 12, 27)
        );

        // Then: Dec 23 (1.0) + Dec 24 (1.0, not half) = 2.0 days
        assertThat(result).isEqualByComparingTo(new BigDecimal("2.0"));
    }

    @Test
    @DisplayName("Dec 24 on weekend (Saturday) doesn't affect calculation")
    void testDecember24OnWeekend() {
        // Given: User with half-day holidays enabled, Dec 24, 2022 is a Saturday
        when(userRepository.findById(1L)).thenReturn(Optional.of(userWithHalfDayHolidays));
        when(workingHoursRepository.findByUserId(1L)).thenReturn(standardWorkingHours);

        // When
        BigDecimal result = calculator.calculateWorkingDays(
                1L,
                GermanState.BERLIN,
                LocalDate.of(2022, 12, 24), // Saturday
                LocalDate.of(2022, 12, 24)
        );

        // Then: Dec 24 is a Saturday (not a working day) = 0 days
        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Dec 31 on Sunday doesn't affect calculation")
    void testDecember31OnWeekend() {
        // Given: User with half-day holidays enabled, Dec 31, 2023 is a Sunday
        when(userRepository.findById(1L)).thenReturn(Optional.of(userWithHalfDayHolidays));
        when(workingHoursRepository.findByUserId(1L)).thenReturn(standardWorkingHours);

        // When
        BigDecimal result = calculator.calculateWorkingDays(
                1L,
                GermanState.BERLIN,
                LocalDate.of(2023, 12, 31), // Sunday
                LocalDate.of(2023, 12, 31)
        );

        // Then: Dec 31 is a Sunday (not a working day) = 0 days
        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Dec 24 as public holiday takes precedence (counts as 0, not 0.5)")
    void testDecember24AsPublicHoliday() {
        // Given: User with half-day holidays enabled, Dec 24 is a public holiday
        when(userRepository.findById(1L)).thenReturn(Optional.of(userWithHalfDayHolidays));
        when(workingHoursRepository.findByUserId(1L)).thenReturn(standardWorkingHours);
        when(germanPublicHolidays.isPublicHoliday(LocalDate.of(2025, 12, 24), GermanState.BERLIN)).thenReturn(true);

        // When
        BigDecimal result = calculator.calculateWorkingDays(
                1L,
                GermanState.BERLIN,
                LocalDate.of(2025, 12, 24),
                LocalDate.of(2025, 12, 24)
        );

        // Then: Public holiday takes precedence = 0 days (not 0.5)
        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Multiple half-day holidays in same vacation period")
    void testBothHalfDayHolidaysInSamePeriod() {
        // Given: User with half-day holidays enabled, vacation spanning both Dec 24 and 31
        // Dec 22 (Mon) = 1.0, Dec 23 (Tue) = 1.0, Dec 24 (Wed) = 0.5,
        // Dec 25-28 = holidays/weekend, Dec 29 (Mon) = 1.0, Dec 30 (Tue) = 1.0, Dec 31 (Wed) = 0.5
        when(userRepository.findById(1L)).thenReturn(Optional.of(userWithHalfDayHolidays));
        when(workingHoursRepository.findByUserId(1L)).thenReturn(standardWorkingHours);
        when(germanPublicHolidays.isPublicHoliday(LocalDate.of(2025, 12, 25), GermanState.BERLIN)).thenReturn(true); // Christmas
        when(germanPublicHolidays.isPublicHoliday(LocalDate.of(2025, 12, 26), GermanState.BERLIN)).thenReturn(true); // 2nd Christmas Day

        // When
        BigDecimal result = calculator.calculateWorkingDays(
                1L,
                GermanState.BERLIN,
                LocalDate.of(2025, 12, 22),
                LocalDate.of(2025, 12, 31)
        );

        // Then: Dec 22 (1.0) + Dec 23 (1.0) + Dec 24 (0.5) + Dec 29 (1.0) + Dec 30 (1.0) + Dec 31 (0.5) = 5.0 days
        assertThat(result).isEqualByComparingTo(new BigDecimal("5.0"));
    }

    @Test
    @DisplayName("Regular week without half-day holidays works normally")
    void testRegularWeekWithoutHalfDayHolidays() {
        // Given: User with half-day holidays enabled, but vacation in October (no half-day holidays)
        when(userRepository.findById(1L)).thenReturn(Optional.of(userWithHalfDayHolidays));
        when(workingHoursRepository.findByUserId(1L)).thenReturn(standardWorkingHours);

        // When: Oct 20-24, 2025 (Mon-Fri)
        BigDecimal result = calculator.calculateWorkingDays(
                1L,
                GermanState.BERLIN,
                LocalDate.of(2025, 10, 20),
                LocalDate.of(2025, 10, 24)
        );

        // Then: 5 full working days
        assertThat(result).isEqualByComparingTo(new BigDecimal("5.0"));
    }

    @Test
    @DisplayName("Empty date range returns zero days")
    void testEmptyDateRange() {
        // Given: Start date after end date - No stubs needed as method returns early

        // When
        BigDecimal result = calculator.calculateWorkingDays(
                1L,
                GermanState.BERLIN,
                LocalDate.of(2025, 12, 31),
                LocalDate.of(2025, 12, 24)
        );

        // Then: 0 days
        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
