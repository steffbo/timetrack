package cc.remer.timetrack.usecase.timeentry.model;

import cc.remer.timetrack.domain.recurringoffday.RecurringOffDay;
import cc.remer.timetrack.domain.timeentry.TimeEntry;
import cc.remer.timetrack.domain.timeoff.TimeOff;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * Daily summary domain model for time tracking.
 */
@Data
@AllArgsConstructor
@Builder
public class DailySummary {
    private LocalDate date;
    private double actualHours;
    private double expectedHours;
    private DailySummaryStatus status;
    private List<TimeEntry> entries;
    private List<TimeOff> timeOffEntries;
    private List<RecurringOffDay> recurringOffDays;
}
