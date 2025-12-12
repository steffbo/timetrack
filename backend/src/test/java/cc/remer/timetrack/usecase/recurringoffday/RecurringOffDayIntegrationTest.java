package cc.remer.timetrack.usecase.recurringoffday;

import cc.remer.timetrack.adapter.persistence.RecurringOffDayRepository;
import cc.remer.timetrack.adapter.persistence.RepositoryTestBase;
import cc.remer.timetrack.adapter.persistence.UserRepository;
import cc.remer.timetrack.api.model.CreateRecurringOffDayRequest;
import cc.remer.timetrack.api.model.RecurringOffDayResponse;
import cc.remer.timetrack.api.model.UpdateRecurringOffDayRequest;
import cc.remer.timetrack.domain.recurringoffday.RecurringOffDay;
import cc.remer.timetrack.domain.recurringoffday.RecurrencePattern;
import cc.remer.timetrack.domain.user.GermanState;
import cc.remer.timetrack.domain.user.Role;
import cc.remer.timetrack.domain.user.User;
import cc.remer.timetrack.exception.ForbiddenException;
import cc.remer.timetrack.exception.RecurringOffDayNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for recurring off-day use cases.
 */
@DisplayName("Recurring Off-Day Integration Tests")
class RecurringOffDayIntegrationTest extends RepositoryTestBase {

    @Autowired
    private GetRecurringOffDays getRecurringOffDays;

    @Autowired
    private CreateRecurringOffDay createRecurringOffDay;

    @Autowired
    private UpdateRecurringOffDay updateRecurringOffDay;

    @Autowired
    private DeleteRecurringOffDay deleteRecurringOffDay;

    private User testUser;
    private User otherUser;

    @BeforeEach
    void setUp() {
        recurringOffDayRepository.deleteAll();
        userRepository.deleteAll();

        testUser = createTestUser();
        otherUser = createOtherTestUser();
    }

    @Test
    @DisplayName("Should create recurring off-day with EVERY_NTH_WEEK pattern")
    void shouldCreateRecurringOffDayWithEveryNthWeekPattern() {
        // Arrange
        CreateRecurringOffDayRequest request = new CreateRecurringOffDayRequest();
        request.setRecurrencePattern(CreateRecurringOffDayRequest.RecurrencePatternEnum.EVERY_NTH_WEEK);
        request.setWeekday(1); // Monday
        request.setWeekInterval(4); // Every 4 weeks
        request.setReferenceDate(LocalDate.of(2025, 1, 6));
        request.setStartDate(LocalDate.of(2025, 1, 1));
        request.setDescription("Every 4th Monday off");

        // Act
        RecurringOffDayResponse response = createRecurringOffDay.execute(testUser.getId(), request);

        // Assert
        assertThat(response.getId()).isNotNull();
        assertThat(response.getUserId()).isEqualTo(testUser.getId());
        assertThat(response.getRecurrencePattern()).isEqualTo(RecurringOffDayResponse.RecurrencePatternEnum.EVERY_NTH_WEEK);
        assertThat(response.getWeekday()).isEqualTo(1);
        assertThat(response.getWeekInterval()).isEqualTo(4);
        assertThat(response.getReferenceDate()).isEqualTo(LocalDate.of(2025, 1, 6));
        assertThat(response.getIsActive()).isTrue();
        assertThat(response.getDescription()).isEqualTo("Every 4th Monday off");
    }

    @Test
    @DisplayName("Should create recurring off-day with NTH_WEEKDAY_OF_MONTH pattern")
    void shouldCreateRecurringOffDayWithNthWeekdayOfMonthPattern() {
        // Arrange
        CreateRecurringOffDayRequest request = new CreateRecurringOffDayRequest();
        request.setRecurrencePattern(CreateRecurringOffDayRequest.RecurrencePatternEnum.NTH_WEEKDAY_OF_MONTH);
        request.setWeekday(1); // Monday
        request.setWeekOfMonth(4); // 4th occurrence
        request.setStartDate(LocalDate.of(2025, 1, 1));
        request.setDescription("4th Monday of every month");

        // Act
        RecurringOffDayResponse response = createRecurringOffDay.execute(testUser.getId(), request);

        // Assert
        assertThat(response.getRecurrencePattern()).isEqualTo(RecurringOffDayResponse.RecurrencePatternEnum.NTH_WEEKDAY_OF_MONTH);
        assertThat(response.getWeekOfMonth()).isEqualTo(4);
        assertThat(response.getWeekInterval()).isNull();
        assertThat(response.getReferenceDate()).isNull();
    }

    @Test
    @DisplayName("Should fail to create with invalid weekday")
    void shouldFailToCreateWithInvalidWeekday() {
        // Arrange
        CreateRecurringOffDayRequest request = new CreateRecurringOffDayRequest();
        request.setRecurrencePattern(CreateRecurringOffDayRequest.RecurrencePatternEnum.EVERY_NTH_WEEK);
        request.setWeekday(8); // Invalid
        request.setWeekInterval(4);
        request.setReferenceDate(LocalDate.of(2025, 1, 6));
        request.setStartDate(LocalDate.of(2025, 1, 1));

        // Act & Assert
        assertThatThrownBy(() -> createRecurringOffDay.execute(testUser.getId(), request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Wochentag muss zwischen 1");
    }

    @Test
    @DisplayName("Should fail to create EVERY_NTH_WEEK without week interval")
    void shouldFailToCreateEveryNthWeekWithoutWeekInterval() {
        // Arrange
        CreateRecurringOffDayRequest request = new CreateRecurringOffDayRequest();
        request.setRecurrencePattern(CreateRecurringOffDayRequest.RecurrencePatternEnum.EVERY_NTH_WEEK);
        request.setWeekday(1);
        request.setReferenceDate(LocalDate.of(2025, 1, 6));
        request.setStartDate(LocalDate.of(2025, 1, 1));
        // Missing weekInterval

        // Act & Assert
        assertThatThrownBy(() -> createRecurringOffDay.execute(testUser.getId(), request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Wochenintervall ist erforderlich");
    }

    @Test
    @DisplayName("Should fail to create NTH_WEEKDAY_OF_MONTH without week of month")
    void shouldFailToCreateNthWeekdayOfMonthWithoutWeekOfMonth() {
        // Arrange
        CreateRecurringOffDayRequest request = new CreateRecurringOffDayRequest();
        request.setRecurrencePattern(CreateRecurringOffDayRequest.RecurrencePatternEnum.NTH_WEEKDAY_OF_MONTH);
        request.setWeekday(1);
        request.setStartDate(LocalDate.of(2025, 1, 1));
        // Missing weekOfMonth

        // Act & Assert
        assertThatThrownBy(() -> createRecurringOffDay.execute(testUser.getId(), request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Woche des Monats muss zwischen 1 und 5 liegen");
    }

    @Test
    @DisplayName("Should get all recurring off-days for user")
    void shouldGetAllRecurringOffDaysForUser() {
        // Arrange
        createRecurringOffDay(testUser, 1, 4, LocalDate.of(2025, 1, 6), LocalDate.of(2025, 1, 1), "Description 1");
        createRecurringOffDay(testUser, 5, 4, LocalDate.of(2025, 1, 10), LocalDate.of(2025, 1, 1), "Description 2");
        createRecurringOffDay(otherUser, 1, 4, LocalDate.of(2025, 1, 6), LocalDate.of(2025, 1, 1), "Other user's off-day");

        // Act
        List<RecurringOffDayResponse> response = getRecurringOffDays.execute(testUser.getId());

        // Assert
        assertThat(response).hasSize(2);
        assertThat(response).allMatch(r -> r.getUserId().equals(testUser.getId()));
    }

    @Test
    @DisplayName("Should update recurring off-day")
    void shouldUpdateRecurringOffDay() {
        // Arrange
        RecurringOffDay entity = createRecurringOffDay(testUser);
        UpdateRecurringOffDayRequest request = new UpdateRecurringOffDayRequest();
        request.setDescription("Updated description");
        request.setIsActive(false);

        // Act
        RecurringOffDayResponse response = updateRecurringOffDay.execute(testUser.getId(), entity.getId(), request);

        // Assert
        assertThat(response.getDescription()).isEqualTo("Updated description");
        assertThat(response.getIsActive()).isFalse();
    }

    @Test
    @DisplayName("Should fail to update other user's recurring off-day")
    void shouldFailToUpdateOtherUsersRecurringOffDay() {
        // Arrange
        RecurringOffDay entity = createRecurringOffDay(otherUser);
        UpdateRecurringOffDayRequest request = new UpdateRecurringOffDayRequest();
        request.setDescription("Updated");

        // Act & Assert
        assertThatThrownBy(() -> updateRecurringOffDay.execute(testUser.getId(), entity.getId(), request))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("keine Berechtigung");
    }

    @Test
    @DisplayName("Should delete recurring off-day")
    void shouldDeleteRecurringOffDay() {
        // Arrange
        RecurringOffDay entity = createRecurringOffDay(testUser);
        Long id = entity.getId();

        // Act
        deleteRecurringOffDay.execute(testUser.getId(), id);

        // Assert
        assertThat(recurringOffDayRepository.findById(id)).isEmpty();
    }

    @Test
    @DisplayName("Should fail to delete other user's recurring off-day")
    void shouldFailToDeleteOtherUsersRecurringOffDay() {
        // Arrange
        RecurringOffDay entity = createRecurringOffDay(otherUser);

        // Act & Assert
        assertThatThrownBy(() -> deleteRecurringOffDay.execute(testUser.getId(), entity.getId()))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("keine Berechtigung");
    }

    @Test
    @DisplayName("Should fail to delete non-existent recurring off-day")
    void shouldFailToDeleteNonExistentRecurringOffDay() {
        // Act & Assert
        assertThatThrownBy(() -> deleteRecurringOffDay.execute(testUser.getId(), 99999L))
                .isInstanceOf(RecurringOffDayNotFoundException.class);
    }

}
