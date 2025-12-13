package cc.remer.timetrack.usecase.report;

import cc.remer.timetrack.domain.user.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Service for generating monthly time report CSVs.
 * CSV can be imported into Excel and printed as needed.
 */
@Service
@Slf4j
public class MonthlyReportCsvGenerator {

    private static final Locale GERMAN_LOCALE = Locale.GERMAN;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("EEE, dd.MM.yyyy", GERMAN_LOCALE);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter MONTH_YEAR_FORMATTER = DateTimeFormatter.ofPattern("MMMM yyyy", GERMAN_LOCALE);
    private static final String CSV_SEPARATOR = ";"; // Use semicolon for German Excel

    /**
     * Generate a monthly time report as CSV.
     *
     * @param year    the year
     * @param month   the month (1-12)
     * @param user    the user for whom the report is generated
     * @param entries list of daily report entries
     * @return CSV as byte array (UTF-8 with BOM for Excel compatibility)
     */
    public byte[] generateMonthlyReport(int year, int month, User user, List<DailyReportEntry> entries) {
        log.info("Generating monthly report CSV for user {} for {}-{}", user.getId(), year, month);

        StringBuilder csv = new StringBuilder();

        // Add BOM for Excel UTF-8 recognition
        csv.append("\uFEFF");

        // Add header
        LocalDate monthDate = LocalDate.of(year, month, 1);
        String monthYearString = monthDate.format(MONTH_YEAR_FORMATTER);
        csv.append("Stundenzettel ").append(capitalize(monthYearString)).append("\n");
        csv.append("Mitarbeiter").append(CSV_SEPARATOR)
                .append(user.getFirstName()).append(" ").append(user.getLastName()).append("\n");
        csv.append("\n");

        // Add table header
        csv.append("Datum").append(CSV_SEPARATOR)
                .append("Anfang").append(CSV_SEPARATOR)
                .append("Pause (min)").append(CSV_SEPARATOR)
                .append("Ende").append(CSV_SEPARATOR)
                .append("Gesamt").append(CSV_SEPARATOR)
                .append("Überstunden").append("\n");

        // Add data rows
        for (DailyReportEntry entry : entries) {
            csv.append(entry.date().format(DATE_FORMATTER)).append(CSV_SEPARATOR)
                    .append(entry.startTime() != null ? entry.startTime().format(TIME_FORMATTER) : "").append(CSV_SEPARATOR)
                    .append(entry.breakMinutes()).append(CSV_SEPARATOR)
                    .append(entry.endTime() != null ? entry.endTime().format(TIME_FORMATTER) : "").append(CSV_SEPARATOR)
                    .append(entry.totalHours() != null ? formatHours(entry.totalHours()) : "").append(CSV_SEPARATOR)
                    .append(entry.overtime() != null ? formatHours(entry.overtime()) : "").append("\n");
        }

        // Add summary
        double totalHours = entries.stream()
                .filter(e -> e.totalHours() != null)
                .mapToDouble(DailyReportEntry::totalHours)
                .sum();

        double totalExpectedHours = entries.stream()
                .filter(e -> e.expectedHours() != null)
                .mapToDouble(DailyReportEntry::expectedHours)
                .sum();

        double totalOvertime = entries.stream()
                .filter(e -> e.overtime() != null)
                .mapToDouble(DailyReportEntry::overtime)
                .sum();

        csv.append("\n");
        csv.append("Zusammenfassung\n");
        csv.append("Gesamtstunden").append(CSV_SEPARATOR).append(formatHours(totalHours)).append("\n");
        csv.append("Sollstunden").append(CSV_SEPARATOR).append(formatHours(totalExpectedHours)).append("\n");
        csv.append("Gesamtüberstunden").append(CSV_SEPARATOR).append(formatHoursWithSign(totalOvertime)).append("\n");

        String csvString = csv.toString();
        log.info("Successfully generated CSV report ({} bytes)", csvString.length());

        return csvString.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Format hours as decimal with 2 decimal places.
     * Uses comma as decimal separator for German locale.
     */
    private String formatHours(Double hours) {
        if (hours == null) {
            return "";
        }
        return String.format(GERMAN_LOCALE, "%.2f", hours);
    }

    /**
     * Format hours with sign (+ or -) for overtime display.
     */
    private String formatHoursWithSign(Double hours) {
        if (hours == null) {
            return "";
        }
        String sign = hours >= 0 ? "+" : "";
        return sign + String.format(GERMAN_LOCALE, "%.2f", hours);
    }

    /**
     * Capitalize the first letter of a string.
     */
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
