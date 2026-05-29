package cc.remer.timetrack.adapter.persistence;

import cc.remer.timetrack.domain.recurringoffday.RecurrencePattern;
import cc.remer.timetrack.domain.recurringoffday.RecurringOffDay;
import cc.remer.timetrack.domain.recurringoffday.RecurringOffDayConflictWarning;
import cc.remer.timetrack.domain.recurringoffday.RecurringOffDayExemption;
import cc.remer.timetrack.domain.timeentry.EntryType;
import cc.remer.timetrack.domain.timeentry.TimeEntry;
import cc.remer.timetrack.domain.timeoff.TimeOff;
import cc.remer.timetrack.domain.timeoff.TimeOffType;
import cc.remer.timetrack.domain.user.InviteToken;
import cc.remer.timetrack.domain.user.User;
import cc.remer.timetrack.domain.vacationbalance.VacationBalance;
import cc.remer.timetrack.domain.workinghours.WorkingHours;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Domain Repository Integration Tests")
class DomainRepositoryIntegrationTest extends RepositoryTestBase {

    @Autowired
    private RecurringOffDayExemptionRepository exemptionRepository;

    @Autowired
    private RecurringOffDayConflictWarningRepository warningRepository;

    @Autowired
    private EntityManager entityManager;

    private User user;

    @BeforeEach
    void setUp() {
        warningRepository.deleteAll();
        timeEntryRepository.deleteAll();
        exemptionRepository.deleteAll();
        recurringOffDayRepository.deleteAll();
        timeOffRepository.deleteAll();
        vacationBalanceRepository.deleteAll();
        inviteTokenRepository.deleteAll();
        workingHoursRepository.deleteAll();
        userRepository.deleteAll();

        user = createTestUser();
    }

    @Test
    @DisplayName("Should find invite token by token and user")
    void shouldFindInviteTokenByTokenAndUser() {
        InviteToken inviteToken = InviteToken.builder()
                .user(user)
                .token("invite-token")
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build();

        inviteTokenRepository.save(inviteToken);

        assertThat(inviteTokenRepository.findByToken("invite-token")).contains(inviteToken);
        assertThat(inviteTokenRepository.findByUserId(user.getId())).contains(inviteToken);
    }

    @Test
    @DisplayName("Should find working hours by user and weekday")
    void shouldFindWorkingHoursByUserAndWeekday() {
        WorkingHours monday = WorkingHours.builder()
                .user(user)
                .weekday((short) 1)
                .hours(BigDecimal.valueOf(8))
                .isWorkingDay(true)
                .breakMinutes(30)
                .build();
        WorkingHours saturday = WorkingHours.builder()
                .user(user)
                .weekday((short) 6)
                .hours(BigDecimal.ZERO)
                .isWorkingDay(false)
                .build();

        workingHoursRepository.save(monday);
        workingHoursRepository.save(saturday);

        assertThat(workingHoursRepository.findByUserId(user.getId()))
                .extracting(WorkingHours::getWeekday)
                .containsExactlyInAnyOrder((short) 1, (short) 6);
        assertThat(workingHoursRepository.findByUserIdAndWeekday(user.getId(), (short) 1))
                .contains(monday);
    }

    @Test
    @DisplayName("Should find time off by order, overlap range, and type year")
    void shouldFindTimeOffByOrderRangeAndTypeYear() {
        TimeOff vacation = createTimeOff(user, LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 12));
        TimeOff sick = createTimeOff(
                user,
                LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 2, 2),
                TimeOffType.SICK,
                "Sick"
        );

        assertThat(timeOffRepository.findByUserIdOrderByStartDateDesc(user.getId()))
                .extracting(TimeOff::getId)
                .containsExactly(sick.getId(), vacation.getId());
        assertThat(timeOffRepository.findByUserIdAndDateRange(
                user.getId(), LocalDate.of(2026, 1, 11), LocalDate.of(2026, 1, 20)))
                .containsExactly(vacation);
        assertThat(timeOffRepository.findByUserIdAndTypeAndYear(
                user.getId(), TimeOffType.VACATION, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)))
                .containsExactly(vacation);
    }

    @Test
    @DisplayName("Should find recurring off days and exemptions")
    void shouldFindRecurringOffDaysAndExemptions() {
        RecurringOffDay recurringOffDay = recurringOffDayRepository.save(RecurringOffDay.builder()
                .user(user)
                .recurrencePattern(RecurrencePattern.EVERY_NTH_WEEK)
                .weekday((short) 1)
                .weekInterval(2)
                .referenceDate(LocalDate.of(2026, 1, 5))
                .startDate(LocalDate.of(2026, 1, 1))
                .isActive(true)
                .build());

        RecurringOffDayExemption first = exemptionRepository.save(RecurringOffDayExemption.builder()
                .recurringOffDay(recurringOffDay)
                .exemptionDate(LocalDate.of(2026, 1, 5))
                .reason("Work day")
                .build());
        RecurringOffDayExemption second = exemptionRepository.save(RecurringOffDayExemption.builder()
                .recurringOffDay(recurringOffDay)
                .exemptionDate(LocalDate.of(2026, 1, 19))
                .reason("Work day")
                .build());

        assertThat(recurringOffDayRepository.findByUserId(user.getId())).containsExactly(recurringOffDay);
        assertThat(recurringOffDayRepository.findActiveByUserIdAndDate(user.getId(), LocalDate.of(2026, 1, 5)))
                .containsExactly(recurringOffDay);
        assertThat(exemptionRepository.findByRecurringOffDayIdOrderByExemptionDateDesc(recurringOffDay.getId()))
                .extracting(RecurringOffDayExemption::getId)
                .containsExactly(second.getId(), first.getId());
        assertThat(exemptionRepository.existsByRecurringOffDayIdAndExemptionDate(
                recurringOffDay.getId(), LocalDate.of(2026, 1, 5))).isTrue();

        exemptionRepository.deleteByRecurringOffDayId(recurringOffDay.getId());

        assertThat(exemptionRepository.findByRecurringOffDayIdOrderByExemptionDateDesc(recurringOffDay.getId()))
                .isEmpty();
    }

    @Test
    @DisplayName("Should find, acknowledge, and delete recurring off-day warnings")
    void shouldFindAcknowledgeAndDeleteConflictWarnings() {
        RecurringOffDay recurringOffDay = recurringOffDayRepository.save(RecurringOffDay.builder()
                .user(user)
                .recurrencePattern(RecurrencePattern.EVERY_NTH_WEEK)
                .weekday((short) 1)
                .weekInterval(2)
                .referenceDate(LocalDate.of(2026, 1, 5))
                .startDate(LocalDate.of(2026, 1, 1))
                .isActive(true)
                .build());
        TimeEntry oldEntry = timeEntryRepository.save(TimeEntry.builder()
                .user(user)
                .entryDate(LocalDate.of(2026, 1, 5))
                .clockIn(LocalDateTime.of(2026, 1, 5, 9, 0))
                .clockOut(LocalDateTime.of(2026, 1, 5, 17, 0))
                .entryType(EntryType.WORK)
                .build());
        TimeEntry newEntry = timeEntryRepository.save(TimeEntry.builder()
                .user(user)
                .entryDate(LocalDate.of(2026, 1, 19))
                .clockIn(LocalDateTime.of(2026, 1, 19, 9, 0))
                .clockOut(LocalDateTime.of(2026, 1, 19, 17, 0))
                .entryType(EntryType.WORK)
                .build());
        RecurringOffDayConflictWarning oldWarning = warningRepository.save(RecurringOffDayConflictWarning.builder()
                .user(user)
                .conflictDate(LocalDate.of(2026, 1, 5))
                .timeEntryId(oldEntry.getId())
                .recurringOffDayId(recurringOffDay.getId())
                .build());
        RecurringOffDayConflictWarning newWarning = warningRepository.save(RecurringOffDayConflictWarning.builder()
                .user(user)
                .conflictDate(LocalDate.of(2026, 1, 19))
                .timeEntryId(newEntry.getId())
                .recurringOffDayId(recurringOffDay.getId())
                .build());

        oldWarning.acknowledge(LocalDateTime.of(2026, 1, 6, 10, 0));
        warningRepository.save(oldWarning);

        assertThat(warningRepository.findByUserIdOrderByConflictDateDesc(user.getId()))
                .extracting(RecurringOffDayConflictWarning::getId)
                .containsExactly(newWarning.getId(), oldWarning.getId());
        assertThat(warningRepository.findByUserIdAndAcknowledgedFalseOrderByConflictDateDesc(user.getId()))
                .containsExactly(newWarning);
        assertThat(warningRepository.findByUserIdAndDateRange(
                user.getId(), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 10)))
                .containsExactly(oldWarning);
        assertThat(warningRepository.findByUserIdAndConflictDate(user.getId(), LocalDate.of(2026, 1, 19)))
                .contains(newWarning);
        assertThat(warningRepository.existsByUserIdAndConflictDate(user.getId(), LocalDate.of(2026, 1, 19)))
                .isTrue();

        warningRepository.deleteByTimeEntryId(newEntry.getId());
        entityManager.clear();

        assertThat(warningRepository.findById(newWarning.getId())).isEmpty();
    }

    @Test
    @DisplayName("Should find vacation balance by user and year")
    void shouldFindVacationBalanceByUserAndYear() {
        VacationBalance balance = createVacationBalance(user, 2026);

        assertThat(vacationBalanceRepository.findByUserIdAndYear(user.getId(), 2026))
                .contains(balance);
        assertThat(vacationBalanceRepository.findByUserIdAndYear(user.getId(), 2027))
                .isEmpty();
    }
}
