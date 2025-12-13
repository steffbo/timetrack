package cc.remer.timetrack.adapter.web;

import cc.remer.timetrack.adapter.security.UserPrincipal;
import cc.remer.timetrack.adapter.web.mapper.TimeEntryMapper;
import cc.remer.timetrack.api.TimeEntriesApi;
import cc.remer.timetrack.api.model.*;
import cc.remer.timetrack.domain.timeentry.EntryType;
import cc.remer.timetrack.domain.timeentry.TimeEntry;
import cc.remer.timetrack.domain.user.User;
import cc.remer.timetrack.usecase.timeentry.*;
import cc.remer.timetrack.usecase.timeentry.model.DailySummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * REST controller for time entry operations.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class TimeEntryController implements TimeEntriesApi {

    private final ClockInUseCase clockInUseCase;
    private final ClockOutUseCase clockOutUseCase;
    private final CreateTimeEntryUseCase createTimeEntryUseCase;
    private final GetTimeEntriesUseCase getTimeEntriesUseCase;
    private final GetDailySummaryUseCase getDailySummaryUseCase;
    private final UpdateTimeEntryUseCase updateTimeEntryUseCase;
    private final DeleteTimeEntryUseCase deleteTimeEntryUseCase;
    private final TimeEntryMapper mapper;

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return User.builder().id(principal.getId()).build();
    }

    @Override
    public ResponseEntity<TimeEntryResponse> clockIn(ClockInRequest request) {
        log.info("POST /api/time-entries/clock-in - Clock in for current user");
        try {
            User user = getCurrentUser();
            TimeEntry entry = clockInUseCase.execute(user, request.getNotes());
            return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(entry));
        } catch (IllegalStateException e) {
            log.warn("Clock in failed: {}", e.getMessage());
            throw e;
        }
    }

    @Override
    public ResponseEntity<TimeEntryResponse> clockOut(ClockOutRequest request) {
        log.info("POST /api/time-entries/clock-out - Clock out for current user");
        try {
            User user = getCurrentUser();
            TimeEntry entry = clockOutUseCase.execute(user, request.getNotes());
            return ResponseEntity.ok(mapper.toResponse(entry));
        } catch (IllegalStateException e) {
            log.warn("Clock out failed: {}", e.getMessage());
            throw e;
        }
    }

    @Override
    public ResponseEntity<TimeEntryResponse> createTimeEntry(CreateTimeEntryRequest request) {
        log.info("POST /api/time-entries - Create manual time entry");
        try {
            User user = getCurrentUser();
            EntryType entryType = EntryType.valueOf(request.getEntryType().name());

            TimeEntry entry = createTimeEntryUseCase.execute(
                    user,
                    mapper.toLocalDateTime(request.getClockIn()),
                    mapper.toLocalDateTime(request.getClockOut()),
                    entryType,
                    request.getNotes()
            );

            return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(entry));
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("Create entry failed: {}", e.getMessage());
            throw e;
        }
    }

    @Override
    public ResponseEntity<List<TimeEntryResponse>> getTimeEntries(LocalDate startDate, LocalDate endDate) {
        log.info("GET /api/time-entries - Get entries: {} to {}", startDate, endDate);
        User user = getCurrentUser();
        List<TimeEntry> entries = getTimeEntriesUseCase.execute(user, startDate, endDate);
        List<TimeEntryResponse> responses = entries.stream()
                .map(mapper::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @Override
    public ResponseEntity<List<DailySummaryResponse>> getDailySummary(LocalDate startDate, LocalDate endDate) {
        log.info("GET /api/time-entries/daily-summary - Get summary: {} to {}", startDate, endDate);
        User user = getCurrentUser();

        // Default to current month if no dates provided
        if (startDate == null || endDate == null) {
            LocalDate now = LocalDate.now();
            startDate = now.withDayOfMonth(1);
            endDate = now.withDayOfMonth(now.lengthOfMonth());
        }

        List<DailySummary> summaries = getDailySummaryUseCase.execute(user, startDate, endDate);
        List<DailySummaryResponse> responses = summaries.stream()
                .map(mapper::toSummaryResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @Override
    public ResponseEntity<TimeEntryResponse> updateTimeEntry(Long id, UpdateTimeEntryRequest request) {
        log.info("PUT /api/time-entries/{} - Update entry", id);
        try {
            User user = getCurrentUser();
            EntryType entryType = EntryType.valueOf(request.getEntryType().name());

            TimeEntry entry = updateTimeEntryUseCase.execute(
                    user, id,
                    mapper.toLocalDateTime(request.getClockIn()),
                    request.getClockOut() != null ? mapper.toLocalDateTime(request.getClockOut()) : null,
                    entryType,
                    request.getNotes()
            );

            return ResponseEntity.ok(mapper.toResponse(entry));
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("Update entry failed: {}", e.getMessage());
            throw e;
        }
    }

    @Override
    public ResponseEntity<Void> deleteTimeEntry(Long id) {
        log.info("DELETE /api/time-entries/{} - Delete entry", id);
        try {
            User user = getCurrentUser();
            deleteTimeEntryUseCase.execute(user, id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            log.warn("Delete entry failed: {}", e.getMessage());
            throw e;
        }
    }
}
