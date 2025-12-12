package cc.remer.timetrack.adapter.persistence;

import cc.remer.timetrack.domain.timeentry.EntryType;
import cc.remer.timetrack.domain.timeentry.TimeEntry;
import cc.remer.timetrack.domain.user.GermanState;
import cc.remer.timetrack.domain.user.Role;
import cc.remer.timetrack.domain.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for TimeEntryRepository.
 */
@DisplayName("TimeEntryRepository Integration Tests")
class TimeEntryRepositoryTest extends RepositoryTestBase {

    @Autowired
    private TimeEntryRepository timeEntryRepository;

    @Autowired
    private UserRepository userRepository;

    private User testUser;
    private TimeEntry testEntry;

    @BeforeEach
    void setUp() {
        timeEntryRepository.deleteAll();
        userRepository.deleteAll();

        testUser = User.builder()
                .email("test@example.com")
                .passwordHash("$2a$10$hashedPassword")
                .firstName("Test")
                .lastName("User")
                .role(Role.USER)
                .active(true)
                .state(GermanState.BERLIN)
                .build();
        testUser = userRepository.save(testUser);

        testEntry = TimeEntry.builder()
                .user(testUser)
                .entryDate(LocalDate.now())
                .clockIn(LocalDateTime.now().withHour(9).withMinute(0).withSecond(0).withNano(0))
                .clockOut(LocalDateTime.now().withHour(17).withMinute(0).withSecond(0).withNano(0))
                .entryType(EntryType.WORK)
                .notes("Test work day")
                .build();
    }

    @Test
    @DisplayName("Should save and find time entry by ID")
    void shouldSaveAndFindTimeEntryById() {
        // When
        TimeEntry savedEntry = timeEntryRepository.save(testEntry);

        // Then
        assertThat(savedEntry.getId()).isNotNull();
        assertThat(savedEntry.getCreatedAt()).isNotNull();

        Optional<TimeEntry> foundEntry = timeEntryRepository.findById(savedEntry.getId());
        assertThat(foundEntry).isPresent();
        assertThat(foundEntry.get().getEntryType()).isEqualTo(EntryType.WORK);
    }

    @Test
    @DisplayName("Should find time entries by user ID")
    void shouldFindTimeEntriesByUserId() {
        // Given
        timeEntryRepository.save(testEntry);

        // When
        List<TimeEntry> entries = timeEntryRepository.findByUserId(testUser.getId());

        // Then
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).getUser().getId()).isEqualTo(testUser.getId());
    }

    @Test
    @DisplayName("Should find time entries by date range")
    void shouldFindTimeEntriesByDateRange() {
        // Given
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        LocalDate tomorrow = today.plusDays(1);

        TimeEntry yesterdayEntry = TimeEntry.builder()
                .user(testUser)
                .entryDate(yesterday)
                .clockIn(yesterday.atTime(9, 0))
                .clockOut(yesterday.atTime(17, 0))
                .entryType(EntryType.WORK)
                .build();

        timeEntryRepository.save(yesterdayEntry);
        timeEntryRepository.save(testEntry);

        // When
        List<TimeEntry> entries = timeEntryRepository.findByUserIdAndEntryDateBetween(
                testUser.getId(), yesterday, today
        );

        // Then
        assertThat(entries).hasSize(2);
    }

    @Test
    @DisplayName("Should find active time entry (not clocked out)")
    void shouldFindActiveTimeEntry() {
        // Given
        TimeEntry activeEntry = TimeEntry.builder()
                .user(testUser)
                .entryDate(LocalDate.now())
                .clockIn(LocalDateTime.now().minusHours(2))
                .clockOut(null)
                .entryType(EntryType.WORK)
                .build();

        timeEntryRepository.save(activeEntry);
        timeEntryRepository.save(testEntry); // This one has clock out

        // When
        Optional<TimeEntry> foundActive = timeEntryRepository.findByUserIdAndClockOutIsNull(testUser.getId());

        // Then
        assertThat(foundActive).isPresent();
        assertThat(foundActive.get().isActive()).isTrue();
        assertThat(foundActive.get().getClockOut()).isNull();
    }

    @Test
    @DisplayName("Should find entries by entry type")
    void shouldFindEntriesByEntryType() {
        // Given
        timeEntryRepository.save(testEntry);

        TimeEntry sickEntry = TimeEntry.builder()
                .user(testUser)
                .entryDate(LocalDate.now().minusDays(1))
                .clockIn(LocalDateTime.now().minusDays(1).withHour(9).withMinute(0))
                .clockOut(LocalDateTime.now().minusDays(1).withHour(17).withMinute(0))
                .entryType(EntryType.SICK)
                .build();
        timeEntryRepository.save(sickEntry);

        // When
        List<TimeEntry> workEntries = timeEntryRepository.findByUserIdAndEntryType(testUser.getId(), EntryType.WORK);
        List<TimeEntry> sickEntries = timeEntryRepository.findByUserIdAndEntryType(testUser.getId(), EntryType.SICK);

        // Then
        assertThat(workEntries).hasSize(1);
        assertThat(sickEntries).hasSize(1);
    }

    @Test
    @DisplayName("Should detect overlapping time entries")
    void shouldDetectOverlappingTimeEntries() {
        // Given
        TimeEntry existingEntry = TimeEntry.builder()
                .user(testUser)
                .entryDate(LocalDate.now())
                .clockIn(LocalDateTime.now().withHour(9).withMinute(0).withSecond(0).withNano(0))
                .clockOut(LocalDateTime.now().withHour(17).withMinute(0).withSecond(0).withNano(0))
                .entryType(EntryType.WORK)
                .build();
        timeEntryRepository.save(existingEntry);

        // When - check for overlap with different scenarios
        boolean overlapsMiddle = timeEntryRepository.hasOverlappingEntries(
                testUser.getId(),
                LocalDateTime.now().withHour(12).withMinute(0).withSecond(0).withNano(0),
                LocalDateTime.now().withHour(14).withMinute(0).withSecond(0).withNano(0),
                null
        );

        boolean overlapsStart = timeEntryRepository.hasOverlappingEntries(
                testUser.getId(),
                LocalDateTime.now().withHour(8).withMinute(0).withSecond(0).withNano(0),
                LocalDateTime.now().withHour(10).withMinute(0).withSecond(0).withNano(0),
                null
        );

        boolean noOverlap = timeEntryRepository.hasOverlappingEntries(
                testUser.getId(),
                LocalDateTime.now().withHour(18).withMinute(0).withSecond(0).withNano(0),
                LocalDateTime.now().withHour(19).withMinute(0).withSecond(0).withNano(0),
                null
        );

        // Then
        assertThat(overlapsMiddle).isTrue();
        assertThat(overlapsStart).isTrue();
        assertThat(noOverlap).isFalse();
    }

    @Test
    @DisplayName("Should calculate total hours worked")
    void shouldCalculateTotalHoursWorked() {
        // Given
        LocalDate startDate = LocalDate.now().minusDays(2);
        LocalDate endDate = LocalDate.now();

        // Day 1: 8 hours
        TimeEntry day1 = TimeEntry.builder()
                .user(testUser)
                .entryDate(startDate)
                .clockIn(startDate.atTime(9, 0))
                .clockOut(startDate.atTime(17, 0))
                .entryType(EntryType.WORK)
                .build();

        // Day 2: 4 hours
        TimeEntry day2 = TimeEntry.builder()
                .user(testUser)
                .entryDate(startDate.plusDays(1))
                .clockIn(startDate.plusDays(1).atTime(9, 0))
                .clockOut(startDate.plusDays(1).atTime(13, 0))
                .entryType(EntryType.WORK)
                .build();

        timeEntryRepository.save(day1);
        timeEntryRepository.save(day2);

        // When
        Long totalMinutes = timeEntryRepository.calculateTotalMinutesWorked(
                testUser.getId(), startDate, endDate
        );

        // Then
        assertThat(totalMinutes).isEqualTo(720); // 12 hours = 720 minutes
    }

    @Test
    @DisplayName("Should calculate duration correctly")
    void shouldCalculateDurationCorrectly() {
        // Given
        TimeEntry savedEntry = timeEntryRepository.save(testEntry);

        // When
        Duration duration = savedEntry.getDuration();
        Double hours = savedEntry.getHoursWorked();

        // Then
        assertThat(duration).isEqualTo(Duration.ofHours(8));
        assertThat(hours).isEqualTo(8.0);
    }

    @Test
    @DisplayName("Should return null duration for active entry")
    void shouldReturnNullDurationForActiveEntry() {
        // Given
        TimeEntry activeEntry = TimeEntry.builder()
                .user(testUser)
                .entryDate(LocalDate.now())
                .clockIn(LocalDateTime.now().minusHours(2))
                .clockOut(null)
                .entryType(EntryType.WORK)
                .build();

        TimeEntry savedEntry = timeEntryRepository.save(activeEntry);

        // When
        Duration duration = savedEntry.getDuration();
        Double hours = savedEntry.getHoursWorked();

        // Then
        assertThat(duration).isNull();
        assertThat(hours).isNull();
    }

    @Test
    @DisplayName("Should check entry type helper methods")
    void shouldCheckEntryTypeHelperMethods() {
        // Given
        TimeEntry workEntry = timeEntryRepository.save(testEntry);

        TimeEntry sickEntry = TimeEntry.builder()
                .user(testUser)
                .entryDate(LocalDate.now())
                .clockIn(LocalDateTime.now().withHour(9).withMinute(0))
                .clockOut(LocalDateTime.now().withHour(17).withMinute(0))
                .entryType(EntryType.SICK)
                .build();
        sickEntry = timeEntryRepository.save(sickEntry);

        // Then
        assertThat(workEntry.isWorkEntry()).isTrue();
        assertThat(workEntry.isSickEntry()).isFalse();
        assertThat(workEntry.isPtoEntry()).isFalse();
        assertThat(workEntry.isEventEntry()).isFalse();

        assertThat(sickEntry.isSickEntry()).isTrue();
        assertThat(sickEntry.isWorkEntry()).isFalse();
    }
}
