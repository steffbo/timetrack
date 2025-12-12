package cc.remer.timetrack.usecase.timeoff;

import cc.remer.timetrack.adapter.persistence.RepositoryTestBase;
import cc.remer.timetrack.adapter.persistence.TimeOffRepository;
import cc.remer.timetrack.adapter.persistence.UserRepository;
import cc.remer.timetrack.api.model.CreateTimeOffRequest;
import cc.remer.timetrack.api.model.TimeOffResponse;
import cc.remer.timetrack.api.model.UpdateTimeOffRequest;
import cc.remer.timetrack.domain.timeoff.TimeOff;
import cc.remer.timetrack.domain.timeoff.TimeOffType;
import cc.remer.timetrack.domain.user.GermanState;
import cc.remer.timetrack.domain.user.Role;
import cc.remer.timetrack.domain.user.User;
import cc.remer.timetrack.exception.ForbiddenException;
import cc.remer.timetrack.exception.TimeOffNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for time-off use cases.
 */
@DisplayName("Time-Off Integration Tests")
class TimeOffIntegrationTest extends RepositoryTestBase {

    @Autowired
    private GetTimeOffEntries getTimeOffEntries;

    @Autowired
    private CreateTimeOff createTimeOff;

    @Autowired
    private UpdateTimeOff updateTimeOff;

    @Autowired
    private DeleteTimeOff deleteTimeOff;

    private User testUser;
    private User otherUser;

    @BeforeEach
    void setUp() {
        timeOffRepository.deleteAll();
        userRepository.deleteAll();

        testUser = createTestUser();
        otherUser = createOtherTestUser();
    }

    @Test
    @DisplayName("Should create vacation time-off")
    void shouldCreateVacationTimeOff() {
        // Arrange
        CreateTimeOffRequest request = new CreateTimeOffRequest();
        request.setStartDate(LocalDate.of(2025, 7, 1));
        request.setEndDate(LocalDate.of(2025, 7, 15));
        request.setTimeOffType(CreateTimeOffRequest.TimeOffTypeEnum.VACATION);
        request.setNotes("Summer vacation");

        // Act
        TimeOffResponse response = createTimeOff.execute(testUser.getId(), request);

        // Assert
        assertThat(response.getId()).isNotNull();
        assertThat(response.getUserId()).isEqualTo(testUser.getId());
        assertThat(response.getStartDate()).isEqualTo(LocalDate.of(2025, 7, 1));
        assertThat(response.getEndDate()).isEqualTo(LocalDate.of(2025, 7, 15));
        assertThat(response.getTimeOffType()).isEqualTo(TimeOffResponse.TimeOffTypeEnum.VACATION);
        assertThat(response.getNotes()).isEqualTo("Summer vacation");
        assertThat(response.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should create sick day time-off")
    void shouldCreateSickDayTimeOff() {
        // Arrange
        CreateTimeOffRequest request = new CreateTimeOffRequest();
        request.setStartDate(LocalDate.of(2025, 3, 10));
        request.setEndDate(LocalDate.of(2025, 3, 12));
        request.setTimeOffType(CreateTimeOffRequest.TimeOffTypeEnum.SICK);
        request.setNotes("Flu");

        // Act
        TimeOffResponse response = createTimeOff.execute(testUser.getId(), request);

        // Assert
        assertThat(response.getTimeOffType()).isEqualTo(TimeOffResponse.TimeOffTypeEnum.SICK);
        assertThat(response.getNotes()).isEqualTo("Flu");
    }

    @Test
    @DisplayName("Should create time-off with custom hours per day")
    void shouldCreateTimeOffWithCustomHoursPerDay() {
        // Arrange
        CreateTimeOffRequest request = new CreateTimeOffRequest();
        request.setStartDate(LocalDate.of(2025, 4, 1));
        request.setEndDate(LocalDate.of(2025, 4, 1));
        request.setTimeOffType(CreateTimeOffRequest.TimeOffTypeEnum.PERSONAL);
        request.setHoursPerDay(4.0); // Half day
        request.setNotes("Doctor appointment");

        // Act
        TimeOffResponse response = createTimeOff.execute(testUser.getId(), request);

        // Assert
        assertThat(response.getHoursPerDay()).isEqualTo(4.0);
    }

    @Test
    @DisplayName("Should fail to create with end date before start date")
    void shouldFailToCreateWithEndDateBeforeStartDate() {
        // Arrange
        CreateTimeOffRequest request = new CreateTimeOffRequest();
        request.setStartDate(LocalDate.of(2025, 7, 15));
        request.setEndDate(LocalDate.of(2025, 7, 1)); // Before start date
        request.setTimeOffType(CreateTimeOffRequest.TimeOffTypeEnum.VACATION);

        // Act & Assert
        assertThatThrownBy(() -> createTimeOff.execute(testUser.getId(), request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Enddatum muss nach dem Startdatum liegen");
    }

    @Test
    @DisplayName("Should fail to create with negative hours per day")
    void shouldFailToCreateWithNegativeHoursPerDay() {
        // Arrange
        CreateTimeOffRequest request = new CreateTimeOffRequest();
        request.setStartDate(LocalDate.of(2025, 7, 1));
        request.setEndDate(LocalDate.of(2025, 7, 1));
        request.setTimeOffType(CreateTimeOffRequest.TimeOffTypeEnum.VACATION);
        request.setHoursPerDay(-1.0);

        // Act & Assert
        assertThatThrownBy(() -> createTimeOff.execute(testUser.getId(), request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dürfen nicht negativ sein");
    }

    @Test
    @DisplayName("Should get all time-off entries for user")
    void shouldGetAllTimeOffEntriesForUser() {
        // Arrange
        createTimeOff(testUser, LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 5), TimeOffType.VACATION, "Test");
        createTimeOff(testUser, LocalDate.of(2025, 7, 1), LocalDate.of(2025, 7, 15), TimeOffType.VACATION, "Test");
        createTimeOff(otherUser, LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 5), TimeOffType.SICK, "Test");

        // Act
        List<TimeOffResponse> response = getTimeOffEntries.execute(testUser.getId(), null, null);

        // Assert
        assertThat(response).hasSize(2);
        assertThat(response).allMatch(r -> r.getUserId().equals(testUser.getId()));
    }

    @Test
    @DisplayName("Should get time-off entries filtered by date range")
    void shouldGetTimeOffEntriesFilteredByDateRange() {
        // Arrange
        createTimeOff(testUser, LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 5));
        createTimeOff(testUser, LocalDate.of(2025, 7, 1), LocalDate.of(2025, 7, 15));
        createTimeOff(testUser, LocalDate.of(2025, 12, 20), LocalDate.of(2025, 12, 31));

        // Act - Get entries in July-August
        List<TimeOffResponse> response = getTimeOffEntries.execute(
                testUser.getId(),
                LocalDate.of(2025, 7, 1),
                LocalDate.of(2025, 8, 31)
        );

        // Assert
        assertThat(response).hasSize(1);
        assertThat(response.get(0).getStartDate()).isEqualTo(LocalDate.of(2025, 7, 1));
    }

    @Test
    @DisplayName("Should update time-off entry")
    void shouldUpdateTimeOffEntry() {
        // Arrange
        TimeOff entity = createTimeOff(testUser, LocalDate.of(2025, 7, 1), LocalDate.of(2025, 7, 15));
        UpdateTimeOffRequest request = new UpdateTimeOffRequest();
        request.setNotes("Updated vacation notes");
        request.setTimeOffType(UpdateTimeOffRequest.TimeOffTypeEnum.PERSONAL);

        // Act
        TimeOffResponse response = updateTimeOff.execute(testUser.getId(), entity.getId(), request);

        // Assert
        assertThat(response.getNotes()).isEqualTo("Updated vacation notes");
        assertThat(response.getTimeOffType()).isEqualTo(TimeOffResponse.TimeOffTypeEnum.PERSONAL);
    }

    @Test
    @DisplayName("Should fail to update other user's time-off")
    void shouldFailToUpdateOtherUsersTimeOff() {
        // Arrange
        TimeOff entity = createTimeOff(otherUser, LocalDate.of(2025, 7, 1), LocalDate.of(2025, 7, 15));
        UpdateTimeOffRequest request = new UpdateTimeOffRequest();
        request.setNotes("Updated");

        // Act & Assert
        assertThatThrownBy(() -> updateTimeOff.execute(testUser.getId(), entity.getId(), request))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("keine Berechtigung");
    }

    @Test
    @DisplayName("Should delete time-off entry")
    void shouldDeleteTimeOffEntry() {
        // Arrange
        TimeOff entity = createTimeOff(testUser, LocalDate.of(2025, 7, 1), LocalDate.of(2025, 7, 15));
        Long id = entity.getId();

        // Act
        deleteTimeOff.execute(testUser.getId(), id);

        // Assert
        assertThat(timeOffRepository.findById(id)).isEmpty();
    }

    @Test
    @DisplayName("Should fail to delete other user's time-off")
    void shouldFailToDeleteOtherUsersTimeOff() {
        // Arrange
        TimeOff entity = createTimeOff(otherUser, LocalDate.of(2025, 7, 1), LocalDate.of(2025, 7, 15));

        // Act & Assert
        assertThatThrownBy(() -> deleteTimeOff.execute(testUser.getId(), entity.getId()))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("keine Berechtigung");
    }

    @Test
    @DisplayName("Should fail to delete non-existent time-off")
    void shouldFailToDeleteNonExistentTimeOff() {
        // Act & Assert
        assertThatThrownBy(() -> deleteTimeOff.execute(testUser.getId(), 99999L))
                .isInstanceOf(TimeOffNotFoundException.class);
    }

}
