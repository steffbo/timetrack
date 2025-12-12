package cc.remer.timetrack.usecase.timeentry.model;

import cc.remer.timetrack.domain.timeentry.TimeEntry;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * Daily summary domain model for time tracking.
 */
@Data
@AllArgsConstructor
public class DailySummary {
    private LocalDate date;
    private double actualHours;
    private double expectedHours;
    private DailySummaryStatus status;
    private List<TimeEntry> entries;
}
