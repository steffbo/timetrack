package cc.remer.timetrack.domain.publicholiday;

import cc.remer.timetrack.domain.user.GermanState;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/**
 * Calculator for German public holidays.
 * Supports Berlin and Brandenburg.
 */
@Component
public class GermanPublicHolidays {

    /**
     * Get all public holidays for a given year and German state.
     *
     * @param year the year
     * @param state the German state
     * @return list of public holiday dates
     */
    public List<LocalDate> getPublicHolidays(int year, GermanState state) {
        List<LocalDate> holidays = new ArrayList<>();

        // Fixed holidays (same for all states)
        holidays.add(LocalDate.of(year, Month.JANUARY, 1));      // New Year's Day
        holidays.add(LocalDate.of(year, Month.MAY, 1));          // Labour Day
        holidays.add(LocalDate.of(year, Month.OCTOBER, 3));      // German Unity Day
        holidays.add(LocalDate.of(year, Month.DECEMBER, 25));    // Christmas Day
        holidays.add(LocalDate.of(year, Month.DECEMBER, 26));    // Boxing Day

        // Movable holidays (based on Easter)
        LocalDate easter = calculateEasterSunday(year);
        holidays.add(easter.minusDays(2));                       // Good Friday
        holidays.add(easter.plusDays(1));                        // Easter Monday
        holidays.add(easter.plusDays(39));                       // Ascension Day
        holidays.add(easter.plusDays(50));                       // Whit Monday

        // State-specific holidays
        switch (state) {
            case BERLIN:
                holidays.add(LocalDate.of(year, Month.MARCH, 8));    // International Women's Day
                break;
            case BRANDENBURG:
                holidays.add(LocalDate.of(year, Month.OCTOBER, 31)); // Reformation Day
                break;
        }

        return holidays;
    }

    /**
     * Check if a given date is a public holiday for the state.
     *
     * @param date the date to check
     * @param state the German state
     * @return true if it's a public holiday
     */
    public boolean isPublicHoliday(LocalDate date, GermanState state) {
        List<LocalDate> holidays = getPublicHolidays(date.getYear(), state);
        return holidays.contains(date);
    }

    /**
     * Calculate Easter Sunday for a given year using the Computus algorithm.
     * (Meeus/Jones/Butcher algorithm)
     *
     * @param year the year
     * @return Easter Sunday date
     */
    private LocalDate calculateEasterSunday(int year) {
        int a = year % 19;
        int b = year / 100;
        int c = year % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31;
        int day = ((h + l - 7 * m + 114) % 31) + 1;

        return LocalDate.of(year, month, day);
    }
}
