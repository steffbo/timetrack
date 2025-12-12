package cc.remer.timetrack.usecase.vacationbalance;

import cc.remer.timetrack.adapter.persistence.RepositoryTestBase;
import cc.remer.timetrack.adapter.persistence.UserRepository;
import cc.remer.timetrack.adapter.persistence.VacationBalanceRepository;
import cc.remer.timetrack.api.model.UpdateVacationBalanceRequest;
import cc.remer.timetrack.api.model.VacationBalanceResponse;
import cc.remer.timetrack.domain.user.GermanState;
import cc.remer.timetrack.domain.user.Role;
import cc.remer.timetrack.domain.user.User;
import cc.remer.timetrack.domain.vacationbalance.VacationBalance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
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

    private User testUser;

    @BeforeEach
    void setUp() {
        vacationBalanceRepository.deleteAll();
        userRepository.deleteAll();

        testUser = createTestUser();
    }

    @Test
    @DisplayName("Should get vacation balance for current year")
    void shouldGetVacationBalanceForCurrentYear() {
        // Arrange
        int currentYear = Year.now().getValue();
        createVacationBalance(testUser, currentYear, 30.0, 5.0, 2.0, 12.0);

        // Act
        VacationBalanceResponse response = getVacationBalance.execute(testUser.getId(), null);

        // Assert
        assertThat(response.getId()).isNotNull();
        assertThat(response.getUserId()).isEqualTo(testUser.getId());
        assertThat(response.getYear()).isEqualTo(currentYear);
        assertThat(response.getAnnualAllowanceDays()).isEqualTo(30.0);
        assertThat(response.getCarriedOverDays()).isEqualTo(5.0);
        assertThat(response.getAdjustmentDays()).isEqualTo(2.0);
        assertThat(response.getUsedDays()).isEqualTo(12.0);
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
    @DisplayName("Should fail to get non-existent balance")
    void shouldFailToGetNonExistentBalance() {
        // Act & Assert
        assertThatThrownBy(() -> getVacationBalance.execute(testUser.getId(), 2024))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Urlaubssaldo für Jahr 2024 nicht gefunden");
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
        assertThat(response.getRemainingDays()).isEqualTo(32.0); // 30 + 5 + 2 - 5 = 32
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
        assertThat(response.getRemainingDays()).isEqualTo(28.0); // 30 + 5 + 3 - 10 = 28
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

}
