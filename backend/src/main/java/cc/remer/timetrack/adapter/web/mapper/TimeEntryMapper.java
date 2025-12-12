package cc.remer.timetrack.adapter.web.mapper;

import cc.remer.timetrack.api.model.DailySummaryResponse;
import cc.remer.timetrack.api.model.TimeEntryResponse;
import cc.remer.timetrack.domain.timeentry.TimeEntry;
import cc.remer.timetrack.usecase.timeentry.model.DailySummary;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Mapper for TimeEntry entities to DTOs.
 */
@Component
public class TimeEntryMapper {

    private static final ZoneId ZONE_ID = ZoneId.systemDefault();

    /**
     * Convert LocalDateTime to OffsetDateTime.
     */
    public OffsetDateTime toOffsetDateTime(LocalDateTime localDateTime) {
        if (localDateTime == null) return null;
        return localDateTime.atZone(ZONE_ID).toOffsetDateTime();
    }

    /**
     * Convert OffsetDateTime to LocalDateTime.
     */
    public LocalDateTime toLocalDateTime(OffsetDateTime offsetDateTime) {
        if (offsetDateTime == null) return null;
        return offsetDateTime.atZoneSameInstant(ZONE_ID).toLocalDateTime();
    }

    /**
     * Map TimeEntry entity to TimeEntryResponse DTO.
     */
    public TimeEntryResponse toResponse(TimeEntry entry) {
        TimeEntryResponse response = new TimeEntryResponse();
        response.setId(entry.getId());
        response.setUserId(entry.getUser().getId());
        response.setEntryDate(entry.getEntryDate());
        response.setClockIn(toOffsetDateTime(entry.getClockIn()));
        response.setClockOut(toOffsetDateTime(entry.getClockOut()));
        response.setEntryType(TimeEntryResponse.EntryTypeEnum.fromValue(entry.getEntryType().name()));
        response.setHoursWorked(entry.getHoursWorked());
        response.setIsActive(entry.isActive());
        response.setNotes(entry.getNotes());
        response.setCreatedAt(toOffsetDateTime(entry.getCreatedAt()));
        response.setUpdatedAt(toOffsetDateTime(entry.getUpdatedAt()));
        return response;
    }

    /**
     * Map DailySummary model to DailySummaryResponse DTO.
     */
    public DailySummaryResponse toSummaryResponse(DailySummary summary) {
        DailySummaryResponse response = new DailySummaryResponse();
        response.setDate(summary.getDate());
        response.setActualHours(summary.getActualHours());
        response.setExpectedHours(summary.getExpectedHours());
        response.setStatus(DailySummaryResponse.StatusEnum.fromValue(summary.getStatus().name()));

        // Map entries
        List<TimeEntryResponse> entryResponses = summary.getEntries().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        response.setEntries(entryResponses);

        return response;
    }
}
