package cc.remer.timetrack.adapter.web;

import cc.remer.timetrack.api.PublicHolidaysApi;
import cc.remer.timetrack.api.model.PublicHolidayResponse;
import cc.remer.timetrack.api.model.PublicHolidaysResponse;
import cc.remer.timetrack.domain.publicholiday.GermanPublicHolidays;
import cc.remer.timetrack.domain.user.GermanState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST controller for public holidays operations.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class PublicHolidaysController implements PublicHolidaysApi {

    private final GermanPublicHolidays germanPublicHolidays;

    // Map of holiday names in German
    private static final Map<String, String> HOLIDAY_NAMES = Map.ofEntries(
            Map.entry("01-01", "Neujahr"),
            Map.entry("05-01", "Tag der Arbeit"),
            Map.entry("10-03", "Tag der Deutschen Einheit"),
            Map.entry("12-25", "1. Weihnachtstag"),
            Map.entry("12-26", "2. Weihnachtstag"),
            Map.entry("03-08", "Internationaler Frauentag"),
            Map.entry("10-31", "Reformationstag")
    );

    @Override
    public ResponseEntity<PublicHolidaysResponse> getPublicHolidays() {
        log.info("GET /public-holidays - Getting public holidays for years 2023-2027 and all states");

        Map<String, Map<String, List<PublicHolidayResponse>>> holidaysByYearAndState = new HashMap<>();

        // Generate holidays for years 2023-2027
        for (int year = 2023; year <= 2027; year++) {
            Map<String, List<PublicHolidayResponse>> stateHolidays = new HashMap<>();

            // Generate for both states
            for (GermanState state : GermanState.values()) {
                List<LocalDate> holidays = germanPublicHolidays.getPublicHolidays(year, state);

                List<PublicHolidayResponse> holidayResponses = holidays.stream()
                        .map(date -> {
                            PublicHolidayResponse holiday = new PublicHolidayResponse();
                            holiday.setDate(date);
                            holiday.setName(getHolidayName(date, state));
                            holiday.setIsStateSpecific(isStateSpecific(date, state));
                            return holiday;
                        })
                        .sorted((h1, h2) -> h1.getDate().compareTo(h2.getDate()))
                        .collect(Collectors.toList());

                stateHolidays.put(state.name(), holidayResponses);
            }

            holidaysByYearAndState.put(String.valueOf(year), stateHolidays);
        }

        PublicHolidaysResponse response = new PublicHolidaysResponse();
        response.setHolidaysByYearAndState(holidaysByYearAndState);

        return ResponseEntity.ok(response);
    }

    /**
     * Get the name of a holiday based on its date.
     */
    private String getHolidayName(LocalDate date, GermanState state) {
        String key = String.format("%02d-%02d", date.getMonthValue(), date.getDayOfMonth());
        String name = HOLIDAY_NAMES.get(key);
        if (name != null) {
            return name;
        }

        // Calculate Easter-based holidays
        LocalDate easterSunday = calculateEasterSunday(date.getYear());
        if (date.equals(easterSunday.minusDays(2))) {
            return "Karfreitag";
        } else if (date.equals(easterSunday.plusDays(1))) {
            return "Ostermontag";
        } else if (date.equals(easterSunday.plusDays(39))) {
            return "Christi Himmelfahrt";
        } else if (date.equals(easterSunday.plusDays(50))) {
            return "Pfingstmontag";
        }

        return "Feiertag";
    }

    /**
     * Check if a holiday is state-specific.
     */
    private boolean isStateSpecific(LocalDate date, GermanState state) {
        String key = String.format("%02d-%02d", date.getMonthValue(), date.getDayOfMonth());
        return "03-08".equals(key) || "10-31".equals(key);
    }

    /**
     * Calculate Easter Sunday for a given year using the Computus algorithm.
     * (Same as GermanPublicHolidays, duplicated here for holiday name calculation)
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
