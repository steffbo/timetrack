package cc.remer.timetrack.usecase.vacationbalance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import cc.remer.timetrack.adapter.persistence.RepositoryTestBase;
import cc.remer.timetrack.api.model.CreateTimeOffRequest;
import cc.remer.timetrack.api.model.TimeOffResponse;
import cc.remer.timetrack.api.model.UpdateVacationBalanceRequest;
import cc.remer.timetrack.api.model.VacationBalanceResponse;
import cc.remer.timetrack.domain.user.GermanState;
import cc.remer.timetrack.domain.user.Role;
import cc.remer.timetrack.domain.user.User;
import cc.remer.timetrack.usecase.timeoff.CreateTimeOff;
import cc.remer.timetrack.usecase.timeoff.DeleteTimeOff;

/**
 * Integration tests for vacation balance use cases.
 */
@DisplayName("Vacation Balance Integration Tests")
class VacationBalanceIntegrationTest extends RepositoryTestBase {

    @Autowired
    private GetVacationBalance getVacationBalance;

    @Autowired
    private UpdateVacationBalance updateVacationBalance;

    @Autowired
    private CreateTimeOff createTimeOff;

    @Autowired
    private DeleteTimeOff deleteTimeOff;

    private User testUser;

    @BeforeEach
    void setUp() {
        timeOffRepository.deleteAll();
        vacationBalanceRepository.deleteAll();
        workingHoursRepository.deleteAll();
        userRepository.deleteAll();

        testUser = createTestUser();
        createDefaultWorkingHours(testUser);
    }

    @Test
    @DisplayName("Should get vacation balance for current year")
    void shouldGetVacationBalanceForCurrentYear() {
        // Arrange
        int currentYear = 2025;
        createVacationBalance(testUser, currentYear, 30.0, 5.0, 2.0, 0.0);

        // Create vacation time-off entries
        // March 10-14, 2025: Mon-Fri = 5 working days
        CreateTimeOffRequest vacation1 = new CreateTimeOffRequest();
        vacation1.setStartDate(LocalDate.of(currentYear, 3, 10));
        vacation1.setEndDate(LocalDate.of(currentYear, 3, 14));
        vacation1.setTimeOffType(CreateTimeOffRequest.TimeOffTypeEnum.VACATION);
        createTimeOff.execute(testUser.getId(), vacation1);

        // July 7-11, 2025: Mon-Fri = 5 working days
        CreateTimeOffRequest vacation2 = new CreateTimeOffRequest();
        vacation2.setStartDate(LocalDate.of(currentYear, 7, 7));
        vacation2.setEndDate(LocalDate.of(currentYear, 7, 11));
        vacation2.setTimeOffType(CreateTimeOffRequest.TimeOffTypeEnum.VACATION);
        createTimeOff.execute(testUser.getId(), vacation2);

        // Act
        VacationBalanceResponse response = getVacationBalance.execute(testUser.getId(), null);

        // Assert
        assertThat(response.getId()).isNotNull();
        assertThat(response.getUserId()).isEqualTo(testUser.getId());
        assertThat(response.getYear()).isEqualTo(currentYear);
        assertThat(response.getAnnualAllowanceDays()).isEqualTo(30.0);
        assertThat(response.getCarriedOverDays()).isEqualTo(5.0);
        assertThat(response.getAdjustmentDays()).isEqualTo(2.0);
        assertThat(response.getUsedDays()).isEqualTo(10.0); // 5 + 5 = 10 working days
        assertThat(response.getRemainingDays()).isEqualTo(27.0); // 30 + 5 + 2 - 10 = 27
    }

    @Test
    @DisplayName("Should get vacation balance for specific year")
    void shouldGetVacationBalanceForSpecificYear() {
        // Arrange
        createVacationBalance(testUser, 2025);

        // Act
        VacationBalanceResponse response = getVacationBalance.execute(testUser.getId(), 2025);

        // Assert
        assertThat(response.getYear()).isEqualTo(2025);
        assertThat(response.getAnnualAllowanceDays()).isEqualTo(30.0);
    }

    @Test
    @DisplayName("Should auto-create vacation balance if not exists")
    void shouldAutoCreateVacationBalanceIfNotExists() {
        // Act - Request balance for 2024 which doesn't exist yet
        VacationBalanceResponse response = getVacationBalance.execute(testUser.getId(), 2024);

        // Assert - Should create a new balance with defaults
        assertThat(response).isNotNull();
        assertThat(response.getYear()).isEqualTo(2024);
        assertThat(response.getAnnualAllowanceDays()).isEqualTo(30.0);
        assertThat(response.getUsedDays()).isEqualTo(0.0);
        assertThat(response.getRemainingDays()).isEqualTo(30.0);
    }

    @Test
    @DisplayName("Should create new vacation balance if not exists")
    void shouldCreateNewVacationBalanceIfNotExists() {
        // Arrange
        UpdateVacationBalanceRequest request = new UpdateVacationBalanceRequest();
        request.setUserId(testUser.getId());
        request.setYear(2025);
        request.setAnnualAllowanceDays(28.0);
        request.setCarriedOverDays(3.0);
        request.setAdjustmentDays(1.0);

        // Act
        VacationBalanceResponse response = updateVacationBalance.execute(testUser.getId(), testUser.getRole(), request);

        // Assert
        assertThat(response.getId()).isNotNull();
        assertThat(response.getYear()).isEqualTo(2025);
        assertThat(response.getAnnualAllowanceDays()).isEqualTo(28.0);
        assertThat(response.getCarriedOverDays()).isEqualTo(3.0);
        assertThat(response.getAdjustmentDays()).isEqualTo(1.0);
        assertThat(response.getRemainingDays()).isEqualTo(32.0); // 28 + 3 + 1 - 0 = 32
    }

    @Test
    @DisplayName("Should update existing vacation balance")
    void shouldUpdateExistingVacationBalance() {
        // Arrange
        createVacationBalance(testUser, 2025, 30.0, 0.0, 0.0, 5.0);

        UpdateVacationBalanceRequest request = new UpdateVacationBalanceRequest();
        request.setUserId(testUser.getId());
        request.setYear(2025);
        request.setCarriedOverDays(5.0); // Add carryover
        request.setAdjustmentDays(2.0); // Add bonus days

        // Act
        VacationBalanceResponse response = updateVacationBalance.execute(testUser.getId(), testUser.getRole(), request);

        // Assert
        assertThat(response.getCarriedOverDays()).isEqualTo(5.0);
        assertThat(response.getAdjustmentDays()).isEqualTo(2.0);
        assertThat(response.getRemainingDays()).isEqualTo(37.0); // 30 + 5 + 2 - 0 = 37 (no planned vacation)
    }

    @Test
    @DisplayName("Should calculate remaining days correctly")
    void shouldCalculateRemainingDaysCorrectly() {
        // Arrange
        UpdateVacationBalanceRequest request = new UpdateVacationBalanceRequest();
        request.setUserId(testUser.getId());
        request.setYear(2025);
        request.setAnnualAllowanceDays(30.0);
        request.setCarriedOverDays(5.0);
        request.setAdjustmentDays(-1.0); // Deduction
        // Used days will be 0 initially

        // Act
        VacationBalanceResponse response = updateVacationBalance.execute(testUser.getId(), testUser.getRole(), request);

        // Assert - 30 + 5 + (-1) - 0 = 34
        assertThat(response.getRemainingDays()).isEqualTo(34.0);
    }

    @Test
    @DisplayName("Should handle partial updates")
    void shouldHandlePartialUpdates() {
        // Arrange
        createVacationBalance(testUser, 2025, 30.0, 5.0, 0.0, 10.0);

        UpdateVacationBalanceRequest request = new UpdateVacationBalanceRequest();
        request.setUserId(testUser.getId());
        request.setYear(2025);
        request.setAdjustmentDays(3.0); // Only update adjustment

        // Act
        VacationBalanceResponse response = updateVacationBalance.execute(testUser.getId(), testUser.getRole(), request);

        // Assert - Other fields should remain unchanged
        assertThat(response.getAnnualAllowanceDays()).isEqualTo(30.0);
        assertThat(response.getCarriedOverDays()).isEqualTo(5.0);
        assertThat(response.getAdjustmentDays()).isEqualTo(3.0);
        assertThat(response.getUsedDays()).isEqualTo(10.0);
        assertThat(response.getRemainingDays()).isEqualTo(38.0); // 30 + 5 + 3 - 0 = 38 (no planned vacation)
    }

    @Test
    @DisplayName("Should use default 30 days when creating new balance without specification")
    void shouldUseDefault30DaysWhenCreatingNewBalance() {
        // Arrange
        UpdateVacationBalanceRequest request = new UpdateVacationBalanceRequest();
        request.setUserId(testUser.getId());
        request.setYear(2025);
        // Not setting annualAllowanceDays

        // Act
        VacationBalanceResponse response = updateVacationBalance.execute(testUser.getId(), testUser.getRole(), request);

        // Assert - Should default to 30 days
        assertThat(response.getAnnualAllowanceDays()).isEqualTo(30.0);
    }

    @Test
    @DisplayName("Should handle negative adjustments")
    void shouldHandleNegativeAdjustments() {
        // Arrange
        createVacationBalance(testUser, 2025);

        UpdateVacationBalanceRequest request = new UpdateVacationBalanceRequest();
        request.setUserId(testUser.getId());
        request.setYear(2025);
        request.setAdjustmentDays(-5.0); // Deduct 5 days

        // Act
        VacationBalanceResponse response = updateVacationBalance.execute(testUser.getId(), testUser.getRole(), request);

        // Assert - 30 + 0 + (-5) - 0 = 25
        assertThat(response.getAdjustmentDays()).isEqualTo(-5.0);
        assertThat(response.getRemainingDays()).isEqualTo(25.0);
    }

    @Test
    @DisplayName("Should automatically update vacation balance when creating vacation time-off")
    void shouldAutoUpdateVacationBalanceWhenCreatingVacation() {
        // Arrange - Create vacation balance for 2025
        createVacationBalance(testUser, 2025, 30.0, 0.0, 0.0, 0.0);

        // Create a 5-day vacation (Jan 6-10, 2025 = Mon-Fri = 5 working days)
        CreateTimeOffRequest request = new CreateTimeOffRequest();
        request.setStartDate(LocalDate.of(2025, 1, 6));
        request.setEndDate(LocalDate.of(2025, 1, 10));
        request.setTimeOffType(CreateTimeOffRequest.TimeOffTypeEnum.VACATION);

        // Act
        createTimeOff.execute(testUser.getId(), request);

        // Assert - Vacation balance should be updated
        VacationBalanceResponse balance = getVacationBalance.execute(testUser.getId(), 2025);
        assertThat(balance.getUsedDays()).isEqualTo(5.0); // 5 working days used
        assertThat(balance.getRemainingDays()).isEqualTo(25.0); // 30 - 5 = 25
    }

    @Test
    @DisplayName("Should automatically update vacation balance when deleting vacation time-off")
    void shouldAutoUpdateVacationBalanceWhenDeletingVacation() {
        // Arrange - Create vacation balance and time-off
        createVacationBalance(testUser, 2025, 30.0, 0.0, 0.0, 0.0);

        // Jan 6-10, 2025 = Mon-Fri = 5 working days
        CreateTimeOffRequest request = new CreateTimeOffRequest();
        request.setStartDate(LocalDate.of(2025, 1, 6));
        request.setEndDate(LocalDate.of(2025, 1, 10));
        request.setTimeOffType(CreateTimeOffRequest.TimeOffTypeEnum.VACATION);

        TimeOffResponse timeOff = createTimeOff.execute(testUser.getId(), request);

        // Verify vacation was deducted
        VacationBalanceResponse balanceAfterCreate = getVacationBalance.execute(testUser.getId(), 2025);
        assertThat(balanceAfterCreate.getUsedDays()).isEqualTo(5.0); // 5 working days

        // Act - Delete the time-off
        deleteTimeOff.execute(testUser.getId(), timeOff.getId());

        // Assert - Vacation balance should be restored
        VacationBalanceResponse balanceAfterDelete = getVacationBalance.execute(testUser.getId(), 2025);
        assertThat(balanceAfterDelete.getUsedDays()).isEqualTo(0.0);
        assertThat(balanceAfterDelete.getRemainingDays()).isEqualTo(30.0);
    }

    @Test
    @DisplayName("Should handle multiple vacation entries in the same year")
    void shouldHandleMultipleVacationEntriesInSameYear() {
        // Arrange - Create vacation balance for 2025
        createVacationBalance(testUser, 2025, 30.0, 0.0, 0.0, 0.0);

        // Create first vacation - March 10-12, 2025 = Mon-Wed = 3 working days
        CreateTimeOffRequest request1 = new CreateTimeOffRequest();
        request1.setStartDate(LocalDate.of(2025, 3, 10));
        request1.setEndDate(LocalDate.of(2025, 3, 12));
        request1.setTimeOffType(CreateTimeOffRequest.TimeOffTypeEnum.VACATION);
        createTimeOff.execute(testUser.getId(), request1);

        // Create second vacation - July 1-5, 2025 = Tue-Sat = 4 working days (Tue-Fri)
        CreateTimeOffRequest request2 = new CreateTimeOffRequest();
        request2.setStartDate(LocalDate.of(2025, 7, 1));
        request2.setEndDate(LocalDate.of(2025, 7, 4)); // Changed to July 4 (Tue-Fri = 4 days)
        request2.setTimeOffType(CreateTimeOffRequest.TimeOffTypeEnum.VACATION);
        createTimeOff.execute(testUser.getId(), request2);

        // Assert - Total used days should be 7 working days
        VacationBalanceResponse balance = getVacationBalance.execute(testUser.getId(), 2025);
        assertThat(balance.getUsedDays()).isEqualTo(7.0); // 3 + 4 = 7 working days
        assertThat(balance.getRemainingDays()).isEqualTo(23.0); // 30 - 7 = 23
    }

    @Test
    @DisplayName("Should not update vacation balance for non-vacation time-off types")
    void shouldNotUpdateVacationBalanceForNonVacationTypes() {
        // Arrange - Create vacation balance for 2025
        createVacationBalance(testUser, 2025, 30.0, 0.0, 0.0, 0.0);

        // Create sick leave (should not affect vacation balance)
        CreateTimeOffRequest request = new CreateTimeOffRequest();
        request.setStartDate(LocalDate.of(2025, 2, 10));
        request.setEndDate(LocalDate.of(2025, 2, 14));
        request.setTimeOffType(CreateTimeOffRequest.TimeOffTypeEnum.SICK);
        createTimeOff.execute(testUser.getId(), request);

        // Assert - Vacation balance should remain unchanged
        VacationBalanceResponse balance = getVacationBalance.execute(testUser.getId(), 2025);
        assertThat(balance.getUsedDays()).isEqualTo(0.0);
        assertThat(balance.getRemainingDays()).isEqualTo(30.0);
    }

    @Test
    @DisplayName("Should create vacation balance automatically if not exists when creating vacation")
    void shouldCreateVacationBalanceIfNotExistsWhenCreatingVacation() {
        // Arrange - No vacation balance exists yet for 2025
        // Create a vacation - June 2-6, 2025 = Mon-Fri = 5 working days
        CreateTimeOffRequest request = new CreateTimeOffRequest();
        request.setStartDate(LocalDate.of(2025, 6, 2));
        request.setEndDate(LocalDate.of(2025, 6, 6));
        request.setTimeOffType(CreateTimeOffRequest.TimeOffTypeEnum.VACATION);

        // Act
        createTimeOff.execute(testUser.getId(), request);

        // Assert - Vacation balance should be created with default 30 days and 5
        // working days used
        VacationBalanceResponse balance = getVacationBalance.execute(testUser.getId(), 2025);
        assertThat(balance).isNotNull();
        assertThat(balance.getAnnualAllowanceDays()).isEqualTo(30.0);
        assertThat(balance.getUsedDays()).isEqualTo(5.0); // 5 working days
        assertThat(balance.getRemainingDays()).isEqualTo(25.0); // 30 - 5 = 25
    }

    @Test
    @DisplayName("Should exclude weekends when calculating vacation days")
    void shouldExcludeWeekendsWhenCalculatingVacationDays() {
        // Arrange - Create vacation balance for 2025
        createVacationBalance(testUser, 2025, 30.0, 0.0, 0.0, 0.0);

        // Create 2-week vacation including 2 weekends
        // Jan 6-17, 2025 = 14 calendar days = 10 working days (Mon-Fri each week)
        CreateTimeOffRequest request = new CreateTimeOffRequest();
        request.setStartDate(LocalDate.of(2025, 1, 6)); // Monday
        request.setEndDate(LocalDate.of(2025, 1, 17)); // Friday
        request.setTimeOffType(CreateTimeOffRequest.TimeOffTypeEnum.VACATION);

        // Act
        createTimeOff.execute(testUser.getId(), request);

        // Assert - Only working days should be counted (weekends excluded)
        VacationBalanceResponse balance = getVacationBalance.execute(testUser.getId(), 2025);
        assertThat(balance.getUsedDays()).isEqualTo(10.0); // 10 working days (2 full weeks Mon-Fri)
        assertThat(balance.getRemainingDays()).isEqualTo(20.0); // 30 - 10 = 20
    }

    @Test
    @DisplayName("Should exclude public holidays when calculating vacation days")
    void shouldExcludePublicHolidaysWhenCalculatingVacationDays() {
        // Arrange - Create vacation balance for 2025
        createVacationBalance(testUser, 2025, 30.0, 0.0, 0.0, 0.0);

        // Create vacation around New Year (includes Jan 1, 2025 = public holiday)
        // Dec 30, 2024 - Jan 2, 2025 = 4 calendar days
        // But Dec 30 is 2024, so only count Jan 2 = 1 working day (Jan 1 is holiday,
        // weekends excluded)
        CreateTimeOffRequest request = new CreateTimeOffRequest();
        request.setStartDate(LocalDate.of(2025, 1, 1)); // Wednesday (New Year - holiday)
        request.setEndDate(LocalDate.of(2025, 1, 3)); // Friday
        request.setTimeOffType(CreateTimeOffRequest.TimeOffTypeEnum.VACATION);

        // Act
        createTimeOff.execute(testUser.getId(), request);

        // Assert - Jan 1 (holiday) should not be counted, only Jan 2-3 (Thu-Fri) = 2
        // working days
        VacationBalanceResponse balance = getVacationBalance.execute(testUser.getId(), 2025);
        assertThat(balance.getUsedDays()).isEqualTo(2.0); // 2 working days (Jan 2-3)
        assertThat(balance.getRemainingDays()).isEqualTo(28.0); // 30 - 2 = 28
    }

    @Test
    @DisplayName("Should exclude recurring off-days when calculating vacation days")
    void shouldExcludeRecurringOffDaysWhenCalculatingVacationDays() {
        // Arrange - Create vacation balance for 2025
        createVacationBalance(testUser, 2025, 30.0, 0.0, 0.0, 0.0);

        // Create a recurring off-day for every other Monday starting Jan 6
        createRecurringOffDay(
                testUser,
                1, // Monday
                2, // Every 2 weeks
                LocalDate.of(2025, 1, 6),
                LocalDate.of(2025, 1, 1),
                "Recurring Monday off");

        // Create vacation Jan 6-9, 2025 (Mon-Thu)
        // Jan 6 is a recurring off-day, so should only count Jan 7-9 = 3 working days
        CreateTimeOffRequest request = new CreateTimeOffRequest();
        request.setStartDate(LocalDate.of(2025, 1, 6)); // Monday (recurring off-day)
        request.setEndDate(LocalDate.of(2025, 1, 9)); // Thursday
        request.setTimeOffType(CreateTimeOffRequest.TimeOffTypeEnum.VACATION);

        // Act
        createTimeOff.execute(testUser.getId(), request);

        // Assert - Jan 6 should not be counted (recurring off-day), only Jan 7-9 = 3
        // working days
        VacationBalanceResponse balance = getVacationBalance.execute(testUser.getId(), 2025);
        assertThat(balance.getUsedDays()).isEqualTo(3.0); // 3 working days (Jan 7-9)
        assertThat(balance.getRemainingDays()).isEqualTo(27.0); // 30 - 3 = 27
    }

    @Test
    @DisplayName("Should exclude sick days when calculating vacation days")
    void shouldExcludeSickDaysWhenCalculatingVacationDays() {
        // Arrange - Create vacation balance for 2025
        createVacationBalance(testUser, 2025, 30.0, 0.0, 0.0, 0.0);

        // Create a 2-week vacation block: Jan 6-17, 2025 (Mon-Fri = 10 working days)
        CreateTimeOffRequest vacationRequest = new CreateTimeOffRequest();
        vacationRequest.setStartDate(LocalDate.of(2025, 1, 6)); // Monday
        vacationRequest.setEndDate(LocalDate.of(2025, 1, 17)); // Friday
        vacationRequest.setTimeOffType(CreateTimeOffRequest.TimeOffTypeEnum.VACATION);
        createTimeOff.execute(testUser.getId(), vacationRequest);

        // Create a sick day within the vacation period: Jan 9-10, 2025 (Thu-Fri = 2
        // working days)
        CreateTimeOffRequest sickRequest = new CreateTimeOffRequest();
        sickRequest.setStartDate(LocalDate.of(2025, 1, 9)); // Thursday
        sickRequest.setEndDate(LocalDate.of(2025, 1, 10)); // Friday
        sickRequest.setTimeOffType(CreateTimeOffRequest.TimeOffTypeEnum.SICK);
        createTimeOff.execute(testUser.getId(), sickRequest);

        // Act - Recalculate to ensure sick days take precedence
        VacationBalanceResponse balance = getVacationBalance.execute(testUser.getId(), 2025);

        // Assert - Only 8 working days should be counted as vacation (10 - 2 sick days)
        assertThat(balance.getUsedDays()).isEqualTo(8.0); // 10 - 2 sick days = 8 vacation days
        assertThat(balance.getRemainingDays()).isEqualTo(22.0); // 30 - 8 = 22
    }

    @Test
    @DisplayName("Should exclude personal days when calculating vacation days")
    void shouldExcludePersonalDaysWhenCalculatingVacationDays() {
        // Arrange - Create vacation balance for 2025
        createVacationBalance(testUser, 2025, 30.0, 0.0, 0.0, 0.0);

        // Create a 3-week vacation block: Feb 3-21, 2025 (Mon-Fri = 15 working days)
        CreateTimeOffRequest vacationRequest = new CreateTimeOffRequest();
        vacationRequest.setStartDate(LocalDate.of(2025, 2, 3)); // Monday
        vacationRequest.setEndDate(LocalDate.of(2025, 2, 21)); // Friday
        vacationRequest.setTimeOffType(CreateTimeOffRequest.TimeOffTypeEnum.VACATION);
        createTimeOff.execute(testUser.getId(), vacationRequest);

        // Create personal days within the vacation period: Feb 10-12, 2025 (Mon-Wed = 3
        // working days)
        CreateTimeOffRequest personalRequest = new CreateTimeOffRequest();
        personalRequest.setStartDate(LocalDate.of(2025, 2, 10)); // Monday
        personalRequest.setEndDate(LocalDate.of(2025, 2, 12)); // Wednesday
        personalRequest.setTimeOffType(CreateTimeOffRequest.TimeOffTypeEnum.PERSONAL);
        createTimeOff.execute(testUser.getId(), personalRequest);

        // Act
        VacationBalanceResponse balance = getVacationBalance.execute(testUser.getId(), 2025);

        // Assert - Only 12 working days should be counted as vacation (15 - 3 personal
        // days)
        assertThat(balance.getUsedDays()).isEqualTo(12.0); // 15 - 3 personal days = 12 vacation days
        assertThat(balance.getRemainingDays()).isEqualTo(18.0); // 30 - 12 = 18
    }

    @Test
    @DisplayName("Should handle complex vacation with multiple exclusions")
    void shouldHandleComplexVacationWithMultipleExclusions() {
        // Arrange - Create vacation balance for 2025
        createVacationBalance(testUser, 2025, 30.0, 0.0, 0.0, 0.0);

        // Create a recurring off-day for Wednesdays starting Feb 5
        createRecurringOffDay(
                testUser,
                3, // Wednesday
                1, // Every week
                LocalDate.of(2025, 2, 5),
                LocalDate.of(2025, 2, 1),
                "Wednesday off");

        // Create a 3-week vacation: Feb 3-21, 2025
        // Total calendar days: 19 days
        // Working days without exclusions: 15 (3 weeks * 5 days)
        // - 3 Wednesdays (recurring off-days): Feb 5, 12, 19
        // - 2 sick days: Feb 10-11 (Mon-Tue)
        // Expected vacation days: 15 - 3 - 2 = 10
        CreateTimeOffRequest vacationRequest = new CreateTimeOffRequest();
        vacationRequest.setStartDate(LocalDate.of(2025, 2, 3)); // Monday
        vacationRequest.setEndDate(LocalDate.of(2025, 2, 21)); // Friday
        vacationRequest.setTimeOffType(CreateTimeOffRequest.TimeOffTypeEnum.VACATION);
        createTimeOff.execute(testUser.getId(), vacationRequest);

        // Create sick days within the vacation period: Feb 10-11 (Mon-Tue)
        CreateTimeOffRequest sickRequest = new CreateTimeOffRequest();
        sickRequest.setStartDate(LocalDate.of(2025, 2, 10)); // Monday
        sickRequest.setEndDate(LocalDate.of(2025, 2, 11)); // Tuesday
        sickRequest.setTimeOffType(CreateTimeOffRequest.TimeOffTypeEnum.SICK);
        createTimeOff.execute(testUser.getId(), sickRequest);

        // Act
        VacationBalanceResponse balance = getVacationBalance.execute(testUser.getId(), 2025);

        // Assert - Should exclude weekends, recurring off-days, and sick days
        assertThat(balance.getUsedDays()).isEqualTo(10.0); // 15 - 3 Wed - 2 sick = 10
        assertThat(balance.getRemainingDays()).isEqualTo(20.0); // 30 - 10 = 20
    }

    @Test
    @DisplayName("Should correctly calculate vacation days for Dec 21-31, 2025 with Christmas holidays")
    void shouldCalculateVacationDaysForChristmasWeek2025() {
        // Arrange - Create vacation balance for 2025
        createVacationBalance(testUser, 2025, 30.0, 0.0, 0.0, 0.0);

        // Create a recurring off-day for only Dec 22 (Monday) - use 5-week interval to
        // avoid Dec 29
        createRecurringOffDay(
                testUser,
                1, // Monday
                5, // Every 5 weeks (so Dec 29 won't match)
                LocalDate.of(2025, 12, 22),
                LocalDate.of(2025, 12, 1),
                "Recurring Monday off");

        // Create vacation from Dec 21-31, 2025
        // Dec 21 (Sun) - weekend
        // Dec 22 (Mon) - recurring off-day
        // Dec 23 (Tue) - working day = 1
        // Dec 24 (Wed) - working day = 2
        // Dec 25 (Thu) - Christmas Day (public holiday)
        // Dec 26 (Fri) - Boxing Day (public holiday)
        // Dec 27 (Sat) - weekend
        // Dec 28 (Sun) - weekend
        // Dec 29 (Mon) - working day = 3
        // Dec 30 (Tue) - working day = 4
        // Dec 31 (Wed) - working day = 5
        // Expected: 5 working days
        CreateTimeOffRequest vacationRequest = new CreateTimeOffRequest();
        vacationRequest.setStartDate(LocalDate.of(2025, 12, 21));
        vacationRequest.setEndDate(LocalDate.of(2025, 12, 31));
        vacationRequest.setTimeOffType(CreateTimeOffRequest.TimeOffTypeEnum.VACATION);
        createTimeOff.execute(testUser.getId(), vacationRequest);

        // Act
        VacationBalanceResponse balance = getVacationBalance.execute(testUser.getId(), 2025);

        // Assert - Should be 5 working days (excluding weekends, recurring off-day, and
        // Christmas holidays)
        // Since this vacation is in the future, it shows up in plannedDays, not
        // usedDays
        assertThat(balance.getPlannedDays()).isEqualTo(5.0);
        assertThat(balance.getUsedDays()).isEqualTo(0.0); // Not yet taken (future vacation)
        assertThat(balance.getRemainingDays()).isEqualTo(25.0); // 30 - 5 planned = 25
    }

    // ==================== Half-Day Holidays Tests ====================

    @Test
    @DisplayName("Should count Dec 24 as 0.5 days when half-day holidays enabled")
    void shouldCountDecember24AsHalfDay() {
        // Arrange - Enable half-day holidays for user
        testUser.setHalfDayHolidaysEnabled(true);
        userRepository.save(testUser);

        createVacationBalance(testUser, 2025, 30.0, 0.0, 0.0, 0.0);

        // Dec 23 (Tue) = 1.0, Dec 24 (Wed) = 0.5, Dec 25-26 = holidays, Dec 27 (Sat) =
        // weekend
        // Expected: 1.5 working days
        CreateTimeOffRequest vacationRequest = new CreateTimeOffRequest();
        vacationRequest.setStartDate(LocalDate.of(2025, 12, 23));
        vacationRequest.setEndDate(LocalDate.of(2025, 12, 27));
        vacationRequest.setTimeOffType(CreateTimeOffRequest.TimeOffTypeEnum.VACATION);
        TimeOffResponse timeOff = createTimeOff.execute(testUser.getId(), vacationRequest);

        // Assert - TimeOff days should show 1.5
        assertThat(timeOff.getDays()).isEqualTo(1.5);

        // Act
        VacationBalanceResponse balance = getVacationBalance.execute(testUser.getId(), 2025);

        // Assert - Vacation balance should deduct 1.5 days
        assertThat(balance.getPlannedDays()).isEqualTo(1.5);
        assertThat(balance.getRemainingDays()).isEqualTo(28.5); // 30 - 1.5 = 28.5
    }

    @Test
    @DisplayName("Should count Dec 31 as 0.5 days when half-day holidays enabled")
    void shouldCountDecember31AsHalfDay() {
        // Arrange - Enable half-day holidays for user
        testUser.setHalfDayHolidaysEnabled(true);
        userRepository.save(testUser);

        createVacationBalance(testUser, 2025, 30.0, 0.0, 0.0, 0.0);

        // Dec 29 (Mon) = 1.0, Dec 30 (Tue) = 1.0, Dec 31 (Wed) = 0.5
        // Expected: 2.5 working days
        CreateTimeOffRequest vacationRequest = new CreateTimeOffRequest();
        vacationRequest.setStartDate(LocalDate.of(2025, 12, 29));
        vacationRequest.setEndDate(LocalDate.of(2025, 12, 31));
        vacationRequest.setTimeOffType(CreateTimeOffRequest.TimeOffTypeEnum.VACATION);
        TimeOffResponse timeOff = createTimeOff.execute(testUser.getId(), vacationRequest);

        // Assert - TimeOff days should show 2.5
        assertThat(timeOff.getDays()).isEqualTo(2.5);

        // Act
        VacationBalanceResponse balance = getVacationBalance.execute(testUser.getId(), 2025);

        // Assert - Vacation balance should deduct 2.5 days
        assertThat(balance.getPlannedDays()).isEqualTo(2.5);
        assertThat(balance.getRemainingDays()).isEqualTo(27.5); // 30 - 2.5 = 27.5
    }

    @Test
    @DisplayName("Should count both Dec 24 and 31 as 0.5 days each")
    void shouldCountBothHalfDayHolidays() {
        // Arrange - Enable half-day holidays for user
        testUser.setHalfDayHolidaysEnabled(true);
        userRepository.save(testUser);

        createVacationBalance(testUser, 2025, 30.0, 0.0, 0.0, 0.0);

        // Dec 22 (Mon) = 1.0, Dec 23 (Tue) = 1.0, Dec 24 (Wed) = 0.5
        // Dec 25-26 = holidays, Dec 27-28 = weekend
        // Dec 29 (Mon) = 1.0, Dec 30 (Tue) = 1.0, Dec 31 (Wed) = 0.5
        // Expected: 5.0 working days
        CreateTimeOffRequest vacationRequest = new CreateTimeOffRequest();
        vacationRequest.setStartDate(LocalDate.of(2025, 12, 22));
        vacationRequest.setEndDate(LocalDate.of(2025, 12, 31));
        vacationRequest.setTimeOffType(CreateTimeOffRequest.TimeOffTypeEnum.VACATION);
        TimeOffResponse timeOff = createTimeOff.execute(testUser.getId(), vacationRequest);

        // Assert - TimeOff days should show 5.0
        assertThat(timeOff.getDays()).isEqualTo(5.0);

        // Act
        VacationBalanceResponse balance = getVacationBalance.execute(testUser.getId(), 2025);

        // Assert - Vacation balance should deduct 5.0 days
        assertThat(balance.getPlannedDays()).isEqualTo(5.0);
        assertThat(balance.getRemainingDays()).isEqualTo(25.0); // 30 - 5.0 = 25.0
    }

    @Test
    @DisplayName("Should count Dec 24 and 31 as full days when feature disabled")
    void shouldCountFullDaysWhenHalfDayHolidaysDisabled() {
        // Arrange - Half-day holidays NOT enabled (default false)
        createVacationBalance(testUser, 2025, 30.0, 0.0, 0.0, 0.0);

        // Dec 23 (Tue) = 1.0, Dec 24 (Wed) = 1.0 (full day, not half)
        // Dec 25-26 = holidays, Dec 27 (Sat) = weekend
        // Expected: 2.0 working days
        CreateTimeOffRequest vacationRequest = new CreateTimeOffRequest();
        vacationRequest.setStartDate(LocalDate.of(2025, 12, 23));
        vacationRequest.setEndDate(LocalDate.of(2025, 12, 27));
        vacationRequest.setTimeOffType(CreateTimeOffRequest.TimeOffTypeEnum.VACATION);
        TimeOffResponse timeOff = createTimeOff.execute(testUser.getId(), vacationRequest);

        // Assert - TimeOff days should show 2.0 (not 1.5)
        assertThat(timeOff.getDays()).isEqualTo(2.0);

        // Act
        VacationBalanceResponse balance = getVacationBalance.execute(testUser.getId(), 2025);

        // Assert - Vacation balance should deduct 2.0 days (not 1.5)
        assertThat(balance.getPlannedDays()).isEqualTo(2.0);
        assertThat(balance.getRemainingDays()).isEqualTo(28.0); // 30 - 2.0 = 28.0
    }

    @Test
    @DisplayName("Should count only Dec 24 as 0.5 days when taking single day")
    void shouldCountSingleDecember24AsHalfDay() {
        // Arrange - Enable half-day holidays for user
        testUser.setHalfDayHolidaysEnabled(true);
        userRepository.save(testUser);

        createVacationBalance(testUser, 2025, 30.0, 0.0, 0.0, 0.0);

        // Only Dec 24 (Wed) = 0.5
        CreateTimeOffRequest vacationRequest = new CreateTimeOffRequest();
        vacationRequest.setStartDate(LocalDate.of(2025, 12, 24));
        vacationRequest.setEndDate(LocalDate.of(2025, 12, 24));
        vacationRequest.setTimeOffType(CreateTimeOffRequest.TimeOffTypeEnum.VACATION);
        TimeOffResponse timeOff = createTimeOff.execute(testUser.getId(), vacationRequest);

        // Assert - TimeOff days should show 0.5
        assertThat(timeOff.getDays()).isEqualTo(0.5);

        // Act
        VacationBalanceResponse balance = getVacationBalance.execute(testUser.getId(), 2025);

        // Assert - Vacation balance should deduct 0.5 days
        assertThat(balance.getPlannedDays()).isEqualTo(0.5);
        assertThat(balance.getRemainingDays()).isEqualTo(29.5); // 30 - 0.5 = 29.5
    }

    @Test
    @DisplayName("Should correctly calculate when deleting vacation with half-day holidays")
    void shouldRecalculateWhenDeletingVacationWithHalfDays() {
        // Arrange - Enable half-day holidays for user
        testUser.setHalfDayHolidaysEnabled(true);
        userRepository.save(testUser);

        createVacationBalance(testUser, 2025, 30.0, 0.0, 0.0, 0.0);

        // Create vacation with Dec 24 (0.5 days)
        CreateTimeOffRequest vacationRequest = new CreateTimeOffRequest();
        vacationRequest.setStartDate(LocalDate.of(2025, 12, 24));
        vacationRequest.setEndDate(LocalDate.of(2025, 12, 24));
        vacationRequest.setTimeOffType(CreateTimeOffRequest.TimeOffTypeEnum.VACATION);
        TimeOffResponse timeOff = createTimeOff.execute(testUser.getId(), vacationRequest);

        // Verify balance after creation
        VacationBalanceResponse balanceAfterCreate = getVacationBalance.execute(testUser.getId(), 2025);
        assertThat(balanceAfterCreate.getPlannedDays()).isEqualTo(0.5);
        assertThat(balanceAfterCreate.getRemainingDays()).isEqualTo(29.5);

        // Act - Delete the vacation
        deleteTimeOff.execute(testUser.getId(), timeOff.getId());

        // Assert - Balance should be restored
        VacationBalanceResponse balanceAfterDelete = getVacationBalance.execute(testUser.getId(), 2025);
        assertThat(balanceAfterDelete.getPlannedDays()).isEqualTo(0.0);
        assertThat(balanceAfterDelete.getRemainingDays()).isEqualTo(30.0); // Restored to full 30
    }

    @Test
    @DisplayName("Should handle multiple vacations with mixed half-day and full-day periods")
    void shouldHandleMultipleVacationsWithMixedDays() {
        // Arrange - Enable half-day holidays for user
        testUser.setHalfDayHolidaysEnabled(true);
        userRepository.save(testUser);

        createVacationBalance(testUser, 2025, 30.0, 0.0, 0.0, 0.0);

        // Vacation 1: Dec 24 only = 0.5 days
        CreateTimeOffRequest vacation1 = new CreateTimeOffRequest();
        vacation1.setStartDate(LocalDate.of(2025, 12, 24));
        vacation1.setEndDate(LocalDate.of(2025, 12, 24));
        vacation1.setTimeOffType(CreateTimeOffRequest.TimeOffTypeEnum.VACATION);
        createTimeOff.execute(testUser.getId(), vacation1);

        // Vacation 2: Regular week in October = 5.0 days
        CreateTimeOffRequest vacation2 = new CreateTimeOffRequest();
        vacation2.setStartDate(LocalDate.of(2025, 10, 20)); // Mon
        vacation2.setEndDate(LocalDate.of(2025, 10, 24)); // Fri
        vacation2.setTimeOffType(CreateTimeOffRequest.TimeOffTypeEnum.VACATION);
        createTimeOff.execute(testUser.getId(), vacation2);

        // Vacation 3: Dec 31 only = 0.5 days
        CreateTimeOffRequest vacation3 = new CreateTimeOffRequest();
        vacation3.setStartDate(LocalDate.of(2025, 12, 31));
        vacation3.setEndDate(LocalDate.of(2025, 12, 31));
        vacation3.setTimeOffType(CreateTimeOffRequest.TimeOffTypeEnum.VACATION);
        createTimeOff.execute(testUser.getId(), vacation3);

        // Act
        VacationBalanceResponse balance = getVacationBalance.execute(testUser.getId(), 2025);

        // Assert - Total: 0.5 + 5.0 + 0.5 = 6.0 days
        assertThat(balance.getPlannedDays()).isEqualTo(6.0);
        assertThat(balance.getRemainingDays()).isEqualTo(24.0); // 30 - 6.0 = 24.0
    }

    @Test
    @DisplayName("Should correctly calculate vacation for Dec 21-30, 2025 with half-day holidays")
    void shouldCalculateDecember21to30WithHalfDayHolidays() {
        // Arrange - Enable half-day holidays for user
        testUser.setHalfDayHolidaysEnabled(true);
        userRepository.save(testUser);

        createVacationBalance(testUser, 2025, 30.0, 0.0, 0.0, 0.0);

        // Create vacation from Dec 21-30, 2025
        // Dec 21 (Sun) = 0 (weekend)
        // Dec 22 (Mon) = 1.0
        // Dec 23 (Tue) = 1.0
        // Dec 24 (Wed) = 0.5 (half-day holiday)
        // Dec 25 (Thu) = 0 (Christmas Day public holiday)
        // Dec 26 (Fri) = 0 (Boxing Day public holiday)
        // Dec 27 (Sat) = 0 (weekend)
        // Dec 28 (Sun) = 0 (weekend)
        // Dec 29 (Mon) = 1.0
        // Dec 30 (Tue) = 1.0
        // Expected: 4.5 working days
        CreateTimeOffRequest vacationRequest = new CreateTimeOffRequest();
        vacationRequest.setStartDate(LocalDate.of(2025, 12, 21));
        vacationRequest.setEndDate(LocalDate.of(2025, 12, 30));
        vacationRequest.setTimeOffType(CreateTimeOffRequest.TimeOffTypeEnum.VACATION);
        TimeOffResponse timeOff = createTimeOff.execute(testUser.getId(), vacationRequest);

        // Assert - TimeOff days should show 4.5
        assertThat(timeOff.getDays()).isEqualTo(4.5);

        // Act
        VacationBalanceResponse balance = getVacationBalance.execute(testUser.getId(), 2025);

        // Assert - Vacation balance should deduct 4.5 days
        assertThat(balance.getPlannedDays()).isEqualTo(4.5);
        assertThat(balance.getRemainingDays()).isEqualTo(25.5); // 30 - 4.5 = 25.5
    }

    // ==================== Authorization Tests ====================

    @Test
    @DisplayName("Should allow user to update their own vacation balance")
    void shouldAllowUserToUpdateOwnVacationBalance() {
        // Arrange
        UpdateVacationBalanceRequest request = new UpdateVacationBalanceRequest();
        request.setUserId(testUser.getId());
        request.setYear(2025);
        request.setAnnualAllowanceDays(28.0);

        // Act - User updating their own balance
        VacationBalanceResponse response = updateVacationBalance.execute(testUser.getId(), testUser.getRole(), request);

        // Assert - Should succeed
        assertThat(response).isNotNull();
        assertThat(response.getAnnualAllowanceDays()).isEqualTo(28.0);
    }

    @Test
    @DisplayName("Should prevent regular user from updating another user's vacation balance")
    void shouldPreventUserFromUpdatingOtherUsersBalance() {
        // Arrange - Create another user
        User otherUser = User.builder()
                .email("other@timetrack.local")
                .passwordHash(passwordEncoder.encode("password"))
                .firstName("Other")
                .lastName("User")
                .role(Role.USER)
                .active(true)
                .state(GermanState.BERLIN)
                .halfDayHolidaysEnabled(false)
                .build();
        otherUser = userRepository.save(otherUser);

        UpdateVacationBalanceRequest request = new UpdateVacationBalanceRequest();
        request.setUserId(otherUser.getId());
        request.setYear(2025);
        request.setAnnualAllowanceDays(28.0);

        // Act & Assert - testUser (regular user) trying to update otherUser's balance
        assertThatThrownBy(() -> updateVacationBalance.execute(testUser.getId(), testUser.getRole(), request))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                .hasMessageContaining("You can only update your own vacation balance");
    }

    @Test
    @DisplayName("Should allow admin to update any user's vacation balance")
    void shouldAllowAdminToUpdateAnyUsersBalance() {
        // Arrange - Create an admin user
        User adminUser = User.builder()
                .email("admin2@timetrack.local")
                .passwordHash(passwordEncoder.encode("admin"))
                .firstName("Admin")
                .lastName("User")
                .role(Role.ADMIN)
                .active(true)
                .state(GermanState.BERLIN)
                .halfDayHolidaysEnabled(false)
                .build();
        adminUser = userRepository.save(adminUser);

        UpdateVacationBalanceRequest request = new UpdateVacationBalanceRequest();
        request.setUserId(testUser.getId()); // Admin updating testUser's balance
        request.setYear(2025);
        request.setAnnualAllowanceDays(35.0);

        // Act - Admin updating another user's balance
        VacationBalanceResponse response = updateVacationBalance.execute(adminUser.getId(), adminUser.getRole(),
                request);

        // Assert - Should succeed
        assertThat(response).isNotNull();
        assertThat(response.getUserId()).isEqualTo(testUser.getId());
        assertThat(response.getAnnualAllowanceDays()).isEqualTo(35.0);
    }

}
