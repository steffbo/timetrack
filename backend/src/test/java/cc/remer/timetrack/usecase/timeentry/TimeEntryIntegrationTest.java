package cc.remer.timetrack.usecase.timeentry;

import cc.remer.timetrack.adapter.persistence.RepositoryTestBase;
import cc.remer.timetrack.domain.timeentry.EntryType;
import cc.remer.timetrack.domain.timeentry.TimeEntry;
import cc.remer.timetrack.domain.user.User;
import cc.remer.timetrack.usecase.timeentry.model.DailySummary;
import cc.remer.timetrack.usecase.timeentry.model.DailySummaryStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for time entry use cases.
 */
@DisplayName("Time Entry Integration Tests")
class TimeEntryIntegrationTest extends RepositoryTestBase {

    @Autowired
    private ClockInUseCase clockInUseCase;

    @Autowired
    private ClockOutUseCase clockOutUseCase;

    @Autowired
    private GetTimeEntriesUseCase getTimeEntriesUseCase;

    @Autowired
    private GetDailySummaryUseCase getDailySummaryUseCase;

    @Autowired
    private UpdateTimeEntryUseCase updateTimeEntryUseCase;

    @Autowired
    private DeleteTimeEntryUseCase deleteTimeEntryUseCase;

    private User testUser;
    private User otherUser;

    @BeforeEach
    void setUp() {
        if (timeEntryRepository != null) timeEntryRepository.deleteAll();
        if (workingHoursRepository != null) workingHoursRepository.deleteAll();
        userRepository.deleteAll();

        testUser = createTestUser();
        otherUser = createOtherTestUser();

        // Create default working hours (8h Mon-Fri) for testing daily summaries
        createDefaultWorkingHours(testUser);
        createDefaultWorkingHours(otherUser);
    }

    // ===== Clock In Tests =====

    @Test
    @DisplayName("Should clock in successfully")
    void shouldClockInSuccessfully() {
        // Act
        TimeEntry entry = clockInUseCase.execute(testUser, "Starting work");

        // Assert
        assertThat(entry.getId()).isNotNull();
        assertThat(entry.getUser().getId()).isEqualTo(testUser.getId());
        assertThat(entry.getClockIn()).isNotNull();
        assertThat(entry.getClockOut()).isNull();
        assertThat(entry.getEntryDate()).isEqualTo(LocalDate.now());
        assertThat(entry.getEntryType()).isEqualTo(EntryType.WORK);
        assertThat(entry.getNotes()).isEqualTo("Starting work");
        assertThat(entry.isActive()).isTrue();
    }

    @Test
    @DisplayName("Should fail to clock in when already clocked in")
    void shouldFailToClockInWhenAlreadyClockedIn() {
        // Arrange
        clockInUseCase.execute(testUser, null);

        // Act & Assert
        assertThatThrownBy(() -> clockInUseCase.execute(testUser, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Bereits eingecheckt");
    }

    @Test
    @DisplayName("Should allow different users to clock in simultaneously")
    void shouldAllowDifferentUsersToClockInSimultaneously() {
        // Act
        TimeEntry entry1 = clockInUseCase.execute(testUser, null);
        TimeEntry entry2 = clockInUseCase.execute(otherUser, null);

        // Assert
        assertThat(entry1.getUser().getId()).isEqualTo(testUser.getId());
        assertThat(entry2.getUser().getId()).isEqualTo(otherUser.getId());
        assertThat(entry1.isActive()).isTrue();
        assertThat(entry2.isActive()).isTrue();
    }

    // ===== Clock Out Tests =====

    @Test
    @DisplayName("Should clock out successfully")
    void shouldClockOutSuccessfully() throws InterruptedException {
        // Arrange
        TimeEntry clockedIn = clockInUseCase.execute(testUser, "Morning session");

        // Wait a brief moment to ensure duration > 0
        Thread.sleep(100);

        // Act
        TimeEntry clockedOut = clockOutUseCase.execute(testUser, "End of day");

        // Assert
        assertThat(clockedOut.getId()).isEqualTo(clockedIn.getId());
        assertThat(clockedOut.getClockOut()).isNotNull();
        assertThat(clockedOut.getClockOut()).isAfter(clockedOut.getClockIn());
        assertThat(clockedOut.isActive()).isFalse();
        assertThat(clockedOut.getHoursWorked()).isNotNull();
        assertThat(clockedOut.getHoursWorked()).isGreaterThanOrEqualTo(0.0);
        assertThat(clockedOut.getNotes()).isEqualTo("End of day");
    }

    @Test
    @DisplayName("Should fail to clock out when not clocked in")
    void shouldFailToClockOutWhenNotClockedIn() {
        // Act & Assert
        assertThatThrownBy(() -> clockOutUseCase.execute(testUser, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Nicht eingecheckt");
    }

    @Test
    @DisplayName("Should update notes when clocking out")
    void shouldUpdateNotesWhenClockingOut() {
        // Arrange
        clockInUseCase.execute(testUser, "Initial notes");

        // Act
        TimeEntry clockedOut = clockOutUseCase.execute(testUser, "Updated notes");

        // Assert
        assertThat(clockedOut.getNotes()).isEqualTo("Updated notes");
    }

    // ===== Get Time Entries Tests =====

    @Test
    @DisplayName("Should get all time entries for user")
    void shouldGetAllTimeEntriesForUser() {
        // Arrange
        createCompletedEntry(testUser, LocalDate.now().minusDays(2), 8.0);
        createCompletedEntry(testUser, LocalDate.now().minusDays(1), 7.5);
        createCompletedEntry(testUser, LocalDate.now(), 8.0);

        // Act
        List<TimeEntry> entries = getTimeEntriesUseCase.execute(testUser, null, null);

        // Assert
        assertThat(entries).hasSize(3);
    }

    @Test
    @DisplayName("Should get time entries within date range")
    void shouldGetTimeEntriesWithinDateRange() {
        // Arrange
        LocalDate startDate = LocalDate.now().minusDays(5);
        LocalDate endDate = LocalDate.now().minusDays(1);

        createCompletedEntry(testUser, startDate.minusDays(1), 8.0); // Outside range
        createCompletedEntry(testUser, startDate, 8.0); // Inside
        createCompletedEntry(testUser, startDate.plusDays(2), 7.5); // Inside
        createCompletedEntry(testUser, endDate, 8.0); // Inside
        createCompletedEntry(testUser, endDate.plusDays(1), 8.0); // Outside range

        // Act
        List<TimeEntry> entries = getTimeEntriesUseCase.execute(testUser, startDate, endDate);

        // Assert
        assertThat(entries).hasSize(3);
    }

    @Test
    @DisplayName("Should return entries sorted by clockIn descending")
    void shouldReturnEntriesSortedByClockInDescending() {
        // Arrange
        createCompletedEntry(testUser, LocalDate.now().minusDays(2), 8.0);
        createCompletedEntry(testUser, LocalDate.now().minusDays(1), 8.0);
        createCompletedEntry(testUser, LocalDate.now(), 8.0);

        // Act
        List<TimeEntry> entries = getTimeEntriesUseCase.execute(testUser, null, null);

        // Assert
        assertThat(entries).hasSize(3);
        assertThat(entries.get(0).getEntryDate()).isEqualTo(LocalDate.now());
        assertThat(entries.get(1).getEntryDate()).isEqualTo(LocalDate.now().minusDays(1));
        assertThat(entries.get(2).getEntryDate()).isEqualTo(LocalDate.now().minusDays(2));
    }

    @Test
    @DisplayName("Should not return other user's entries")
    void shouldNotReturnOtherUsersEntries() {
        // Arrange
        createCompletedEntry(testUser, LocalDate.now(), 8.0);
        createCompletedEntry(otherUser, LocalDate.now(), 8.0);

        // Act
        List<TimeEntry> entries = getTimeEntriesUseCase.execute(testUser, null, null);

        // Assert
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).getUser().getId()).isEqualTo(testUser.getId());
    }

    // ===== Get Daily Summary Tests =====

    @Test
    @DisplayName("Should calculate daily summary with matched hours")
    void shouldCalculateDailySummaryWithMatchedHours() {
        // Arrange
        LocalDate today = LocalDate.now();
        createCompletedEntry(testUser, today, 8.0); // Expected is 8h for weekdays

        // Act
        List<DailySummary> summaries = getDailySummaryUseCase.execute(
                testUser, today, today);

        // Assert
        assertThat(summaries).hasSize(1);
        DailySummary summary = summaries.get(0);
        assertThat(summary.getDate()).isEqualTo(today);
        assertThat(summary.getActualHours()).isEqualTo(8.0);
        assertThat(summary.getExpectedHours()).isGreaterThan(0.0); // Should have working hours config
        assertThat(summary.getStatus()).isEqualTo(DailySummaryStatus.MATCHED);
        assertThat(summary.getEntries()).hasSize(1);
    }

    @Test
    @DisplayName("Should calculate daily summary with below expected hours")
    void shouldCalculateDailySummaryWithBelowExpectedHours() {
        // Arrange
        LocalDate today = LocalDate.now();
        createCompletedEntry(testUser, today, 6.0); // Less than expected 8h

        // Act
        List<DailySummary> summaries = getDailySummaryUseCase.execute(
                testUser, today, today);

        // Assert
        DailySummary summary = summaries.get(0);
        assertThat(summary.getActualHours()).isEqualTo(6.0);
        assertThat(summary.getStatus()).isEqualTo(DailySummaryStatus.BELOW_EXPECTED);
    }

    @Test
    @DisplayName("Should calculate daily summary with above expected hours")
    void shouldCalculateDailySummaryWithAboveExpectedHours() {
        // Arrange
        LocalDate today = LocalDate.now();
        createCompletedEntry(testUser, today, 10.0); // More than expected 8h

        // Act
        List<DailySummary> summaries = getDailySummaryUseCase.execute(
                testUser, today, today);

        // Assert
        DailySummary summary = summaries.get(0);
        assertThat(summary.getActualHours()).isEqualTo(10.0);
        assertThat(summary.getStatus()).isEqualTo(DailySummaryStatus.ABOVE_EXPECTED);
    }

    @Test
    @DisplayName("Should show no entry status for days without entries")
    void shouldShowNoEntryStatusForDaysWithoutEntries() {
        // Arrange
        LocalDate today = LocalDate.now();

        // Act
        List<DailySummary> summaries = getDailySummaryUseCase.execute(
                testUser, today, today);

        // Assert
        DailySummary summary = summaries.get(0);
        assertThat(summary.getActualHours()).isEqualTo(0.0);
        assertThat(summary.getStatus()).isEqualTo(DailySummaryStatus.NO_ENTRY);
        assertThat(summary.getEntries()).isEmpty();
    }

    @Test
    @DisplayName("Should generate summary for date range")
    void shouldGenerateSummaryForDateRange() {
        // Arrange
        LocalDate startDate = LocalDate.now().minusDays(3);
        LocalDate endDate = LocalDate.now();
        createCompletedEntry(testUser, startDate, 8.0);
        createCompletedEntry(testUser, endDate, 8.0);

        // Act
        List<DailySummary> summaries = getDailySummaryUseCase.execute(
                testUser, startDate, endDate);

        // Assert
        assertThat(summaries).hasSize(4); // 4 days inclusive
    }

    @Test
    @DisplayName("Should aggregate multiple entries per day")
    void shouldAggregateMultipleEntriesPerDay() {
        // Arrange
        LocalDate today = LocalDate.now();
        createCompletedEntry(testUser, today, 4.0);
        createCompletedEntry(testUser, today, 4.0);

        // Act
        List<DailySummary> summaries = getDailySummaryUseCase.execute(
                testUser, today, today);

        // Assert
        DailySummary summary = summaries.get(0);
        assertThat(summary.getActualHours()).isEqualTo(8.0);
        assertThat(summary.getEntries()).hasSize(2);
    }

    // ===== Update Time Entry Tests =====

    @Test
    @DisplayName("Should update time entry successfully")
    void shouldUpdateTimeEntrySuccessfully() {
        // Arrange
        TimeEntry entry = createCompletedEntry(testUser, LocalDate.now(), 8.0);
        LocalDateTime newClockIn = LocalDateTime.now().minusHours(9);
        LocalDateTime newClockOut = LocalDateTime.now().minusHours(1);

        // Act
        TimeEntry updated = updateTimeEntryUseCase.execute(
                testUser, entry.getId(), newClockIn, newClockOut,
                EntryType.WORK, "Updated notes");

        // Assert
        assertThat(updated.getId()).isEqualTo(entry.getId());
        assertThat(updated.getClockIn()).isEqualTo(newClockIn);
        assertThat(updated.getClockOut()).isEqualTo(newClockOut);
        assertThat(updated.getNotes()).isEqualTo("Updated notes");
    }

    @Test
    @DisplayName("Should fail to update non-existent entry")
    void shouldFailToUpdateNonExistentEntry() {
        // Act & Assert
        assertThatThrownBy(() -> updateTimeEntryUseCase.execute(
                testUser, 99999L, LocalDateTime.now(), LocalDateTime.now(),
                EntryType.WORK, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nicht gefunden");
    }

    @Test
    @DisplayName("Should fail to update other user's entry")
    void shouldFailToUpdateOtherUsersEntry() {
        // Arrange
        TimeEntry entry = createCompletedEntry(otherUser, LocalDate.now(), 8.0);

        // Act & Assert
        assertThatThrownBy(() -> updateTimeEntryUseCase.execute(
                testUser, entry.getId(), LocalDateTime.now(), LocalDateTime.now(),
                EntryType.WORK, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("keine Berechtigung");
    }

    @Test
    @DisplayName("Should fail to update with clockOut before clockIn")
    void shouldFailToUpdateWithClockOutBeforeClockIn() {
        // Arrange
        TimeEntry entry = createCompletedEntry(testUser, LocalDate.now(), 8.0);
        LocalDateTime clockIn = LocalDateTime.now();
        LocalDateTime clockOut = LocalDateTime.now().minusHours(1); // Before clockIn

        // Act & Assert
        assertThatThrownBy(() -> updateTimeEntryUseCase.execute(
                testUser, entry.getId(), clockIn, clockOut, EntryType.WORK, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Checkout-Zeit muss nach der Checkin-Zeit liegen");
    }

    // ===== Delete Time Entry Tests =====

    @Test
    @DisplayName("Should delete time entry successfully")
    void shouldDeleteTimeEntrySuccessfully() {
        // Arrange
        TimeEntry entry = createCompletedEntry(testUser, LocalDate.now(), 8.0);

        // Act
        deleteTimeEntryUseCase.execute(testUser, entry.getId());

        // Assert
        assertThat(timeEntryRepository.findById(entry.getId())).isEmpty();
    }

    @Test
    @DisplayName("Should fail to delete non-existent entry")
    void shouldFailToDeleteNonExistentEntry() {
        // Act & Assert
        assertThatThrownBy(() -> deleteTimeEntryUseCase.execute(testUser, 99999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nicht gefunden");
    }

    @Test
    @DisplayName("Should fail to delete other user's entry")
    void shouldFailToDeleteOtherUsersEntry() {
        // Arrange
        TimeEntry entry = createCompletedEntry(otherUser, LocalDate.now(), 8.0);

        // Act & Assert
        assertThatThrownBy(() -> deleteTimeEntryUseCase.execute(testUser, entry.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("keine Berechtigung");
    }

    // ===== Helper Methods =====

    private TimeEntry createCompletedEntry(User user, LocalDate date, double hours) {
        LocalDateTime clockIn = date.atTime(8, 0);
        LocalDateTime clockOut = clockIn.plusHours((long) hours);

        TimeEntry entry = TimeEntry.builder()
                .user(user)
                .entryDate(date)
                .clockIn(clockIn)
                .clockOut(clockOut)
                .entryType(EntryType.WORK)
                .build();

        return timeEntryRepository.save(entry);
    }
}
