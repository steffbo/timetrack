package cc.remer.timetrack.usecase.report;

import cc.remer.timetrack.domain.timeoff.TimeOffType;
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
         * Total hours worked on this day (null if no entries)
         */
        Double totalHours,

        /**
         * Expected hours for this day from working hours configuration
         */
        Double expectedHours,

        /**
         * Overtime: totalHours - expectedHours (null if active entries)
         */
        Double overtime,

        /**
         * Type of day (weekend, sick, vacation, public holiday, or regular work day)
         */
        DayType dayType,

        /**
         * Notes from time entry or time-off entry
         */
        String notes,

        /**
         * Time-off type for this day (if applicable)
         */
        TimeOffType timeOffType
) {
    /**
     * Types of days for color coding in reports.
     */
    public enum DayType {
        REGULAR,           // Regular work day
        WEEKEND,           // Weekend day
        SICK,              // Sick day
        VACATION,          // Vacation day
        PUBLIC_HOLIDAY,    // Public holiday
        RECURRING_OFF_DAY  // Recurring off-day (e.g., every 4th Monday)
    }
}
