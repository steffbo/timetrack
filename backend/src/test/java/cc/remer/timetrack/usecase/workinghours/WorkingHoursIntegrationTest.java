package cc.remer.timetrack.usecase.workinghours;

import cc.remer.timetrack.adapter.persistence.RepositoryTestBase;
import cc.remer.timetrack.adapter.persistence.UserRepository;
import cc.remer.timetrack.adapter.persistence.WorkingHoursRepository;
import cc.remer.timetrack.api.model.UpdateWorkingDayConfig;
import cc.remer.timetrack.api.model.UpdateWorkingHoursRequest;
import cc.remer.timetrack.api.model.WorkingDayConfig;
import cc.remer.timetrack.api.model.WorkingHoursResponse;
import cc.remer.timetrack.domain.user.GermanState;
import cc.remer.timetrack.domain.user.Role;
import cc.remer.timetrack.domain.user.User;
import cc.remer.timetrack.domain.workinghours.WorkingHours;
import cc.remer.timetrack.exception.ForbiddenException;
import cc.remer.timetrack.exception.UserNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for working hours use cases.
 */
@DisplayName("Working Hours Integration Tests")
class WorkingHoursIntegrationTest extends RepositoryTestBase {

    @Autowired
    private GetWorkingHours getWorkingHours;

    @Autowired
    private UpdateWorkingHours updateWorkingHours;

    private User testAdmin;
    private User testUser;

    @BeforeEach
    void setUp() {
        // Clean up
        workingHoursRepository.deleteAll();
        userRepository.deleteAll();

        // Create test users
        testAdmin = createTestAdmin();
        testUser = createTestUser();

        // Create default working hours for test user
        createDefaultWorkingHours(testUser);
    }

    @Test
    @DisplayName("Should get working hours for authenticated user")
    void shouldGetWorkingHoursForAuthenticatedUser() {
        // When
        WorkingHoursResponse response = getWorkingHours.execute(testUser.getId());

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getUserId()).isEqualTo(testUser.getId());
        assertThat(response.getWorkingDays()).hasSize(7);

        // Verify Monday-Friday have 8 hours
        List<WorkingDayConfig> weekdays = response.getWorkingDays().stream()
                .filter(day -> day.getWeekday() >= 1 && day.getWeekday() <= 5)
                .toList();
        assertThat(weekdays).hasSize(5);
        weekdays.forEach(day -> {
            assertThat(day.getHours()).isEqualTo(8.0);
            assertThat(day.getIsWorkingDay()).isTrue();
        });

        // Verify Saturday-Sunday have 0 hours
        List<WorkingDayConfig> weekends = response.getWorkingDays().stream()
                .filter(day -> day.getWeekday() >= 6 && day.getWeekday() <= 7)
                .toList();
        assertThat(weekends).hasSize(2);
        weekends.forEach(day -> {
            assertThat(day.getHours()).isEqualTo(0.0);
            assertThat(day.getIsWorkingDay()).isFalse();
        });
    }

    @Test
    @DisplayName("Should throw exception when user not found")
    void shouldThrowExceptionWhenUserNotFound() {
        // When/Then
        assertThatThrownBy(() -> getWorkingHours.execute(99999L))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("Benutzer nicht gefunden");
    }

    @Test
    @DisplayName("Admin should get working hours for specific user")
    void adminShouldGetWorkingHoursForSpecificUser() {
        // Given
        createDefaultWorkingHours(testAdmin);

        // When
        WorkingHoursResponse response = getWorkingHours.executeForUser(
                testAdmin.getId(),
                testAdmin.getRole(),
                testUser.getId()
        );

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getUserId()).isEqualTo(testUser.getId());
        assertThat(response.getWorkingDays()).hasSize(7);
    }

    @Test
    @DisplayName("Regular user should not get working hours for other user")
    void regularUserShouldNotGetWorkingHoursForOtherUser() {
        // Given
        createDefaultWorkingHours(testAdmin);

        // When/Then
        assertThatThrownBy(() -> getWorkingHours.executeForUser(
                testUser.getId(),
                testUser.getRole(),
                testAdmin.getId()
        ))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Keine Berechtigung");
    }

    @Test
    @DisplayName("Should update working hours successfully")
    void shouldUpdateWorkingHoursSuccessfully() {
        // Given
        UpdateWorkingHoursRequest request = createUpdateRequest(
                new double[]{7.0, 7.0, 7.0, 7.0, 6.0, 0.0, 0.0},
                new boolean[]{true, true, true, true, true, false, false}
        );

        // When
        WorkingHoursResponse response = updateWorkingHours.execute(testUser.getId(), request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getUserId()).isEqualTo(testUser.getId());
        assertThat(response.getWorkingDays()).hasSize(7);

        // Verify Monday-Thursday have 7 hours
        List<WorkingDayConfig> monToThu = response.getWorkingDays().stream()
                .filter(day -> day.getWeekday() >= 1 && day.getWeekday() <= 4)
                .toList();
        assertThat(monToThu).hasSize(4);
        monToThu.forEach(day -> assertThat(day.getHours()).isEqualTo(7.0));

        // Verify Friday has 6 hours
        WorkingDayConfig friday = response.getWorkingDays().stream()
                .filter(day -> day.getWeekday() == 5)
                .findFirst()
                .orElseThrow();
        assertThat(friday.getHours()).isEqualTo(6.0);

        // Verify weekends have 0 hours
        List<WorkingDayConfig> weekends = response.getWorkingDays().stream()
                .filter(day -> day.getWeekday() >= 6)
                .toList();
        weekends.forEach(day -> assertThat(day.getHours()).isEqualTo(0.0));
    }

    @Test
    @DisplayName("Should validate request has exactly 7 days")
    void shouldValidateRequestHasExactly7Days() {
        // Given - create request with only 5 days
        UpdateWorkingHoursRequest request = new UpdateWorkingHoursRequest();
        List<UpdateWorkingDayConfig> workingDays = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            UpdateWorkingDayConfig dayConfig = new UpdateWorkingDayConfig();
            dayConfig.setWeekday(i);
            dayConfig.setHours(8.0);
            dayConfig.setIsWorkingDay(true);
            workingDays.add(dayConfig);
        }
        request.setWorkingDays(workingDays);

        // When/Then
        assertThatThrownBy(() -> updateWorkingHours.execute(testUser.getId(), request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Es müssen genau 7 Wochentage konfiguriert werden");
    }

    @Test
    @DisplayName("Should validate no duplicate weekdays")
    void shouldValidateNoDuplicateWeekdays() {
        // Given - create request with duplicate Monday (exactly 7 days but with duplicate)
        UpdateWorkingHoursRequest request = new UpdateWorkingHoursRequest();
        List<UpdateWorkingDayConfig> workingDays = new ArrayList<>();

        // Add Monday twice
        for (int i = 0; i < 2; i++) {
            UpdateWorkingDayConfig dayConfig = new UpdateWorkingDayConfig();
            dayConfig.setWeekday(1);
            dayConfig.setHours(8.0);
            dayConfig.setIsWorkingDay(true);
            workingDays.add(dayConfig);
        }

        // Add other days (Tuesday through Saturday only - 5 days)
        for (int i = 2; i <= 6; i++) {
            UpdateWorkingDayConfig dayConfig = new UpdateWorkingDayConfig();
            dayConfig.setWeekday(i);
            dayConfig.setHours(8.0);
            dayConfig.setIsWorkingDay(true);
            workingDays.add(dayConfig);
        }
        request.setWorkingDays(workingDays);

        // When/Then
        assertThatThrownBy(() -> updateWorkingHours.execute(testUser.getId(), request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Doppelter Wochentag");
    }

    @Test
    @DisplayName("Should validate hours are within valid range")
    void shouldValidateHoursAreWithinValidRange() {
        // Given - create request with invalid hours (25 hours)
        UpdateWorkingHoursRequest request = createUpdateRequest(
                new double[]{25.0, 8.0, 8.0, 8.0, 8.0, 0.0, 0.0},
                new boolean[]{true, true, true, true, true, false, false}
        );

        // When/Then
        assertThatThrownBy(() -> updateWorkingHours.execute(testUser.getId(), request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Stunden müssen zwischen 0 und 24 liegen");
    }

    @Test
    @DisplayName("Should validate negative hours are not allowed")
    void shouldValidateNegativeHoursNotAllowed() {
        // Given - create request with negative hours
        UpdateWorkingHoursRequest request = createUpdateRequest(
                new double[]{-1.0, 8.0, 8.0, 8.0, 8.0, 0.0, 0.0},
                new boolean[]{true, true, true, true, true, false, false}
        );

        // When/Then
        assertThatThrownBy(() -> updateWorkingHours.execute(testUser.getId(), request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Stunden müssen zwischen 0 und 24 liegen");
    }

    @Test
    @DisplayName("Should validate invalid weekday values")
    void shouldValidateInvalidWeekdayValues() {
        // Given - create request with invalid weekday (8)
        UpdateWorkingHoursRequest request = new UpdateWorkingHoursRequest();
        List<UpdateWorkingDayConfig> workingDays = new ArrayList<>();

        for (int i = 1; i <= 6; i++) {
            UpdateWorkingDayConfig dayConfig = new UpdateWorkingDayConfig();
            dayConfig.setWeekday(i);
            dayConfig.setHours(8.0);
            dayConfig.setIsWorkingDay(true);
            workingDays.add(dayConfig);
        }

        // Add invalid weekday
        UpdateWorkingDayConfig invalidDay = new UpdateWorkingDayConfig();
        invalidDay.setWeekday(8);
        invalidDay.setHours(8.0);
        invalidDay.setIsWorkingDay(true);
        workingDays.add(invalidDay);

        request.setWorkingDays(workingDays);

        // When/Then
        assertThatThrownBy(() -> updateWorkingHours.execute(testUser.getId(), request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Wochentag muss zwischen 1");
    }

    @Test
    @DisplayName("Should allow part-time working hours configuration")
    void shouldAllowPartTimeWorkingHoursConfiguration() {
        // Given - part-time configuration: 4 hours per day, Monday-Friday
        UpdateWorkingHoursRequest request = createUpdateRequest(
                new double[]{4.0, 4.0, 4.0, 4.0, 4.0, 0.0, 0.0},
                new boolean[]{true, true, true, true, true, false, false}
        );

        // When
        WorkingHoursResponse response = updateWorkingHours.execute(testUser.getId(), request);

        // Then
        assertThat(response).isNotNull();
        List<WorkingDayConfig> weekdays = response.getWorkingDays().stream()
                .filter(day -> day.getWeekday() >= 1 && day.getWeekday() <= 5)
                .toList();
        weekdays.forEach(day -> assertThat(day.getHours()).isEqualTo(4.0));
    }

    @Test
    @DisplayName("Should allow flexible working hours with different hours per day")
    void shouldAllowFlexibleWorkingHours() {
        // Given - flexible configuration
        UpdateWorkingHoursRequest request = createUpdateRequest(
                new double[]{8.0, 6.0, 8.0, 6.0, 4.0, 0.0, 0.0},
                new boolean[]{true, true, true, true, true, false, false}
        );

        // When
        WorkingHoursResponse response = updateWorkingHours.execute(testUser.getId(), request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getWorkingDays()).hasSize(7);

        // Verify each day individually
        WorkingDayConfig monday = findDay(response, 1);
        assertThat(monday.getHours()).isEqualTo(8.0);

        WorkingDayConfig tuesday = findDay(response, 2);
        assertThat(tuesday.getHours()).isEqualTo(6.0);

        WorkingDayConfig wednesday = findDay(response, 3);
        assertThat(wednesday.getHours()).isEqualTo(8.0);

        WorkingDayConfig thursday = findDay(response, 4);
        assertThat(thursday.getHours()).isEqualTo(6.0);

        WorkingDayConfig friday = findDay(response, 5);
        assertThat(friday.getHours()).isEqualTo(4.0);
    }

    private UpdateWorkingHoursRequest createUpdateRequest(double[] hours, boolean[] isWorkingDay) {
        UpdateWorkingHoursRequest request = new UpdateWorkingHoursRequest();
        List<UpdateWorkingDayConfig> workingDays = new ArrayList<>();

        for (int i = 0; i < 7; i++) {
            UpdateWorkingDayConfig dayConfig = new UpdateWorkingDayConfig();
            dayConfig.setWeekday(i + 1);
            dayConfig.setHours(hours[i]);
            dayConfig.setIsWorkingDay(isWorkingDay[i]);
            workingDays.add(dayConfig);
        }

        request.setWorkingDays(workingDays);
        return request;
    }

    private WorkingDayConfig findDay(WorkingHoursResponse response, int weekday) {
        return response.getWorkingDays().stream()
                .filter(day -> day.getWeekday() == weekday)
                .findFirst()
                .orElseThrow();
    }

    @Test
    @DisplayName("Should calculate hours from start and end time")
    void shouldCalculateHoursFromStartAndEndTime() {
        // Given - create request with time values
        UpdateWorkingHoursRequest request = new UpdateWorkingHoursRequest();
        List<UpdateWorkingDayConfig> workingDays = new ArrayList<>();

        // Monday: 09:00 - 17:00 (8 hours)
        UpdateWorkingDayConfig monday = new UpdateWorkingDayConfig();
        monday.setWeekday(1);
        monday.setHours(0.0); // This should be overridden by time calculation
        monday.setIsWorkingDay(true);
        monday.setStartTime("09:00");
        monday.setEndTime("17:00");
        workingDays.add(monday);

        // Tuesday: 08:00 - 16:30 (8.5 hours)
        UpdateWorkingDayConfig tuesday = new UpdateWorkingDayConfig();
        tuesday.setWeekday(2);
        tuesday.setHours(0.0);
        tuesday.setIsWorkingDay(true);
        tuesday.setStartTime("08:00");
        tuesday.setEndTime("16:30");
        workingDays.add(tuesday);

        // Rest of the week without times
        for (int i = 3; i <= 7; i++) {
            UpdateWorkingDayConfig dayConfig = new UpdateWorkingDayConfig();
            dayConfig.setWeekday(i);
            dayConfig.setHours(i <= 5 ? 8.0 : 0.0);
            dayConfig.setIsWorkingDay(i <= 5);
            workingDays.add(dayConfig);
        }

        request.setWorkingDays(workingDays);

        // When
        WorkingHoursResponse response = updateWorkingHours.execute(testUser.getId(), request);

        // Then
        assertThat(response).isNotNull();

        WorkingDayConfig mondayResult = findDay(response, 1);
        assertThat(mondayResult.getHours()).isEqualTo(8.0);
        assertThat(mondayResult.getStartTime()).isEqualTo("09:00");
        assertThat(mondayResult.getEndTime()).isEqualTo("17:00");

        WorkingDayConfig tuesdayResult = findDay(response, 2);
        assertThat(tuesdayResult.getHours()).isEqualTo(8.5);
        assertThat(tuesdayResult.getStartTime()).isEqualTo("08:00");
        assertThat(tuesdayResult.getEndTime()).isEqualTo("16:30");
    }

    @Test
    @DisplayName("Should validate that both start and end time must be set together")
    void shouldValidateBothTimesRequired() {
        // Given - create request with only start time
        UpdateWorkingHoursRequest request = new UpdateWorkingHoursRequest();
        List<UpdateWorkingDayConfig> workingDays = new ArrayList<>();

        UpdateWorkingDayConfig monday = new UpdateWorkingDayConfig();
        monday.setWeekday(1);
        monday.setHours(8.0);
        monday.setIsWorkingDay(true);
        monday.setStartTime("09:00");
        // endTime is not set
        workingDays.add(monday);

        for (int i = 2; i <= 7; i++) {
            UpdateWorkingDayConfig dayConfig = new UpdateWorkingDayConfig();
            dayConfig.setWeekday(i);
            dayConfig.setHours(i <= 5 ? 8.0 : 0.0);
            dayConfig.setIsWorkingDay(i <= 5);
            workingDays.add(dayConfig);
        }

        request.setWorkingDays(workingDays);

        // When/Then
        assertThatThrownBy(() -> updateWorkingHours.execute(testUser.getId(), request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Start- und Endzeit müssen beide angegeben werden oder beide leer sein");
    }

    @Test
    @DisplayName("Should validate that end time is after start time")
    void shouldValidateEndTimeAfterStartTime() {
        // Given - create request with end time before start time
        UpdateWorkingHoursRequest request = new UpdateWorkingHoursRequest();
        List<UpdateWorkingDayConfig> workingDays = new ArrayList<>();

        UpdateWorkingDayConfig monday = new UpdateWorkingDayConfig();
        monday.setWeekday(1);
        monday.setHours(8.0);
        monday.setIsWorkingDay(true);
        monday.setStartTime("17:00");
        monday.setEndTime("09:00"); // End before start
        workingDays.add(monday);

        for (int i = 2; i <= 7; i++) {
            UpdateWorkingDayConfig dayConfig = new UpdateWorkingDayConfig();
            dayConfig.setWeekday(i);
            dayConfig.setHours(i <= 5 ? 8.0 : 0.0);
            dayConfig.setIsWorkingDay(i <= 5);
            workingDays.add(dayConfig);
        }

        request.setWorkingDays(workingDays);

        // When/Then
        assertThatThrownBy(() -> updateWorkingHours.execute(testUser.getId(), request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Endzeit muss nach Startzeit liegen");
    }

    @Test
    @DisplayName("Should allow mixed configuration with and without times")
    void shouldAllowMixedConfiguration() {
        // Given - some days with times, some without
        UpdateWorkingHoursRequest request = new UpdateWorkingHoursRequest();
        List<UpdateWorkingDayConfig> workingDays = new ArrayList<>();

        // Monday with times
        UpdateWorkingDayConfig monday = new UpdateWorkingDayConfig();
        monday.setWeekday(1);
        monday.setHours(0.0);
        monday.setIsWorkingDay(true);
        monday.setStartTime("09:00");
        monday.setEndTime("17:00");
        workingDays.add(monday);

        // Tuesday-Friday without times
        for (int i = 2; i <= 5; i++) {
            UpdateWorkingDayConfig dayConfig = new UpdateWorkingDayConfig();
            dayConfig.setWeekday(i);
            dayConfig.setHours(7.5);
            dayConfig.setIsWorkingDay(true);
            workingDays.add(dayConfig);
        }

        // Weekend
        for (int i = 6; i <= 7; i++) {
            UpdateWorkingDayConfig dayConfig = new UpdateWorkingDayConfig();
            dayConfig.setWeekday(i);
            dayConfig.setHours(0.0);
            dayConfig.setIsWorkingDay(false);
            workingDays.add(dayConfig);
        }

        request.setWorkingDays(workingDays);

        // When
        WorkingHoursResponse response = updateWorkingHours.execute(testUser.getId(), request);

        // Then
        assertThat(response).isNotNull();

        // Monday should have calculated hours and times
        WorkingDayConfig mondayResult = findDay(response, 1);
        assertThat(mondayResult.getHours()).isEqualTo(8.0);
        assertThat(mondayResult.getStartTime()).isNotNull();
        assertThat(mondayResult.getEndTime()).isNotNull();

        // Tuesday should have manual hours and no times
        WorkingDayConfig tuesdayResult = findDay(response, 2);
        assertThat(tuesdayResult.getHours()).isEqualTo(7.5);
        assertThat(tuesdayResult.getStartTime()).isNull();
        assertThat(tuesdayResult.getEndTime()).isNull();
    }
}
