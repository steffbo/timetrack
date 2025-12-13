package cc.remer.timetrack.usecase.report;

import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Daily report entry containing aggregated information for a single day.
 * Used for generating monthly PDF reports.
 */
@Builder
public record DailyReportEntry(
        /**
         * The date for this entry
         */
        LocalDate date,

        /**
         * First clock-in time of the day (null if no entries)
         */
        LocalTime startTime,

        /**
         * Last clock-out time of the day (null if active entry or no entries)
         */
        LocalTime endTime,

        /**
         * Break time in minutes (currently always 0)
         */
        int breakMinutes,

        /**
         * Total hours worked on this day (null if any active entries)
         */
        Double totalHours,

        /**
         * Expected hours for this day from working hours configuration
         */
        Double expectedHours,

        /**
         * Overtime: totalHours - expectedHours (null if active entries)
         */
        Double overtime
) {
}
