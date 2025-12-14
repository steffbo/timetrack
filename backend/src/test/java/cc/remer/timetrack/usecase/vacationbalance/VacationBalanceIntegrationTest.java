package cc.remer.timetrack.usecase.vacationbalance;

import cc.remer.timetrack.adapter.persistence.RepositoryTestBase;
import cc.remer.timetrack.adapter.persistence.UserRepository;
import cc.remer.timetrack.adapter.persistence.VacationBalanceRepository;
import cc.remer.timetrack.api.model.CreateTimeOffRequest;
import cc.remer.timetrack.api.model.TimeOffResponse;
import cc.remer.timetrack.api.model.UpdateVacationBalanceRequest;
import cc.remer.timetrack.api.model.VacationBalanceResponse;
import cc.remer.timetrack.domain.user.GermanState;
import cc.remer.timetrack.domain.user.Role;
import cc.remer.timetrack.domain.user.User;
import cc.remer.timetrack.domain.vacationbalance.VacationBalance;
import cc.remer.timetrack.usecase.timeoff.CreateTimeOff;
import cc.remer.timetrack.usecase.timeoff.DeleteTimeOff;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;

import static org.assertj.core.api.Assertions.*;

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
        userRepository.deleteAll();

        testUser = createTestUser();
    }

    @Test
    @DisplayName("Should get vacation balance for current year")
    void shouldGetVacationBalanceForCurrentYear() {
        // Arrange
        int currentYear = Year.now().getValue();
        createVacationBalance(testUser, currentYear, 30.0, 5.0, 2.0, 0.0);

        // Create vacation time-off entries totaling 12 days
        CreateTimeOffRequest vacation1 = new CreateTimeOffRequest();
        vacation1.setStartDate(LocalDate.of(currentYear, 3, 10));
        vacation1.setEndDate(LocalDate.of(currentYear, 3, 14)); // 5 days
        vacation1.setTimeOffType(CreateTimeOffRequest.TimeOffTypeEnum.VACATION);
        createTimeOff.execute(testUser.getId(), vacation1);

        CreateTimeOffRequest vacation2 = new CreateTimeOffRequest();
        vacation2.setStartDate(LocalDate.of(currentYear, 7, 1));
        vacation2.setEndDate(LocalDate.of(currentYear, 7, 7)); // 7 days
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
        assertThat(response.getUsedDays()).isEqualTo(12.0); // 5 + 7 = 12
        assertThat(response.getRemainingDays()).isEqualTo(25.0); // 30 + 5 + 2 - 12 = 25
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
        VacationBalanceResponse response = updateVacationBalance.execute(request);

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
        VacationBalanceResponse response = updateVacationBalance.execute(request);

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
        VacationBalanceResponse response = updateVacationBalance.execute(request);

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
        VacationBalanceResponse response = updateVacationBalance.execute(request);

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
        VacationBalanceResponse response = updateVacationBalance.execute(request);

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
        VacationBalanceResponse response = updateVacationBalance.execute(request);

        // Assert - 30 + 0 + (-5) - 0 = 25
        assertThat(response.getAdjustmentDays()).isEqualTo(-5.0);
        assertThat(response.getRemainingDays()).isEqualTo(25.0);
    }

    @Test
    @DisplayName("Should automatically update vacation balance when creating vacation time-off")
    void shouldAutoUpdateVacationBalanceWhenCreatingVacation() {
        // Arrange - Create vacation balance for 2025
        createVacationBalance(testUser, 2025, 30.0, 0.0, 0.0, 0.0);

        // Create a 5-day vacation (Jan 6-10, 2025)
        CreateTimeOffRequest request = new CreateTimeOffRequest();
        request.setStartDate(LocalDate.of(2025, 1, 6));
        request.setEndDate(LocalDate.of(2025, 1, 10));
        request.setTimeOffType(CreateTimeOffRequest.TimeOffTypeEnum.VACATION);

        // Act
        createTimeOff.execute(testUser.getId(), request);

        // Assert - Vacation balance should be updated
        VacationBalanceResponse balance = getVacationBalance.execute(testUser.getId(), 2025);
        assertThat(balance.getUsedDays()).isEqualTo(5.0); // 5 days used
        assertThat(balance.getRemainingDays()).isEqualTo(25.0); // 30 - 5 = 25
    }

    @Test
    @DisplayName("Should automatically update vacation balance when deleting vacation time-off")
    void shouldAutoUpdateVacationBalanceWhenDeletingVacation() {
        // Arrange - Create vacation balance and time-off
        createVacationBalance(testUser, 2025, 30.0, 0.0, 0.0, 0.0);

        CreateTimeOffRequest request = new CreateTimeOffRequest();
        request.setStartDate(LocalDate.of(2025, 1, 6));
        request.setEndDate(LocalDate.of(2025, 1, 10));
        request.setTimeOffType(CreateTimeOffRequest.TimeOffTypeEnum.VACATION);

        TimeOffResponse timeOff = createTimeOff.execute(testUser.getId(), request);

        // Verify vacation was deducted
        VacationBalanceResponse balanceAfterCreate = getVacationBalance.execute(testUser.getId(), 2025);
        assertThat(balanceAfterCreate.getUsedDays()).isEqualTo(5.0);

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

        // Create first vacation (3 days)
        CreateTimeOffRequest request1 = new CreateTimeOffRequest();
        request1.setStartDate(LocalDate.of(2025, 3, 10));
        request1.setEndDate(LocalDate.of(2025, 3, 12));
        request1.setTimeOffType(CreateTimeOffRequest.TimeOffTypeEnum.VACATION);
        createTimeOff.execute(testUser.getId(), request1);

        // Create second vacation (5 days)
        CreateTimeOffRequest request2 = new CreateTimeOffRequest();
        request2.setStartDate(LocalDate.of(2025, 7, 1));
        request2.setEndDate(LocalDate.of(2025, 7, 5));
        request2.setTimeOffType(CreateTimeOffRequest.TimeOffTypeEnum.VACATION);
        createTimeOff.execute(testUser.getId(), request2);

        // Assert - Total used days should be 8
        VacationBalanceResponse balance = getVacationBalance.execute(testUser.getId(), 2025);
        assertThat(balance.getUsedDays()).isEqualTo(8.0); // 3 + 5 = 8
        assertThat(balance.getRemainingDays()).isEqualTo(22.0); // 30 - 8 = 22
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
        // Create a vacation
        CreateTimeOffRequest request = new CreateTimeOffRequest();
        request.setStartDate(LocalDate.of(2025, 6, 1));
        request.setEndDate(LocalDate.of(2025, 6, 7));
        request.setTimeOffType(CreateTimeOffRequest.TimeOffTypeEnum.VACATION);

        // Act
        createTimeOff.execute(testUser.getId(), request);

        // Assert - Vacation balance should be created with default 30 days and 7 days used
        VacationBalanceResponse balance = getVacationBalance.execute(testUser.getId(), 2025);
        assertThat(balance).isNotNull();
        assertThat(balance.getAnnualAllowanceDays()).isEqualTo(30.0);
        assertThat(balance.getUsedDays()).isEqualTo(7.0);
        assertThat(balance.getRemainingDays()).isEqualTo(23.0); // 30 - 7 = 23
    }

}
