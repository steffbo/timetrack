package cc.remer.timetrack.usecase.report;

import cc.remer.timetrack.domain.user.User;
import cc.remer.timetrack.usecase.report.DailyReportEntry.DayType;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Service for generating monthly time report PDFs using Apache PDFBox 3.x.
 */
@Service
@Slf4j
public class MonthlyReportPdfGenerator {

    private static final Locale GERMAN_LOCALE = Locale.GERMAN;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("EEE, dd.MM.yyyy", GERMAN_LOCALE);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter MONTH_YEAR_FORMATTER = DateTimeFormatter.ofPattern("MMMM yyyy", GERMAN_LOCALE);

    private static final float MARGIN = 50;
    private static final float TITLE_FONT_SIZE = 18;
    private static final float HEADER_FONT_SIZE = 10;
    private static final float NORMAL_FONT_SIZE = 9;
    private static final float SMALL_FONT_SIZE = 8;

    /**
     * Generate a monthly time report PDF.
     *
     * @param year    the year
     * @param month   the month (1-12)
     * @param user    the user for whom the report is generated
     * @param entries list of daily report entries
     * @return PDF as byte array
     */
    public byte[] generateMonthlyReport(int year, int month, User user, List<DailyReportEntry> entries) {
        log.info("Generating monthly report PDF for user {} for {}-{}", user.getId(), year, month);

        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                float yPosition = page.getMediaBox().getHeight() - MARGIN;

                // Add header
                yPosition = addHeader(contentStream, yPosition, year, month, user);

                // Add spacing
                yPosition -= 20;

                // Add table
                yPosition = addTimeEntriesTable(contentStream, yPosition, page.getMediaBox().getWidth(), entries);

                // Add summary
                addSummary(contentStream, yPosition, entries);
            }

            document.save(outputStream);
            byte[] pdfBytes = outputStream.toByteArray();

            log.info("Successfully generated PDF report ({} bytes)", pdfBytes.length);
            return pdfBytes;

        } catch (IOException e) {
            log.error("Error generating PDF report", e);
            throw new RuntimeException("Failed to generate PDF report", e);
        }
    }

    /**
     * Add header section with title and user information.
     */
    private float addHeader(PDPageContentStream contentStream, float yPosition, int year, int month, User user) throws IOException {
        LocalDate monthDate = LocalDate.of(year, month, 1);
        String monthYearString = monthDate.format(MONTH_YEAR_FORMATTER);
        String title = "Stundenzettel " + capitalize(monthYearString);

        // Title
        contentStream.beginText();
        contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), TITLE_FONT_SIZE);
        contentStream.newLineAtOffset(MARGIN + 150, yPosition);
        contentStream.showText(title);
        contentStream.endText();

        yPosition -= 30;

        // User info
        contentStream.beginText();
        contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
        contentStream.newLineAtOffset(MARGIN, yPosition);
        contentStream.showText("Mitarbeiter: " + user.getFirstName() + " " + user.getLastName());
        contentStream.endText();

        yPosition -= 20;

        return yPosition;
    }

    /**
     * Add table with daily time entries.
     */
    private float addTimeEntriesTable(PDPageContentStream contentStream, float yPosition, float pageWidth, List<DailyReportEntry> entries) throws IOException {
        float tableWidth = pageWidth - 2 * MARGIN;
        float[] columnWidths = {0.30f, 0.12f, 0.12f, 0.12f, 0.12f, 0.12f}; // Relative widths
        float rowHeight = 18;

        // Draw header row
        yPosition = drawTableHeaderRow(contentStream, yPosition, MARGIN, columnWidths, tableWidth);

        // Draw data rows
        for (DailyReportEntry entry : entries) {
            yPosition = drawTableDataRow(contentStream, yPosition, MARGIN, columnWidths, tableWidth, entry, rowHeight);
        }

        return yPosition;
    }

    /**
     * Draw table header row.
     */
    private float drawTableHeaderRow(PDPageContentStream contentStream, float yPosition, float xStart, float[] columnWidths, float tableWidth) throws IOException {
        String[] headers = {"Datum", "Anfang", "Pause", "Ende", "Gesamt", "Uberstd."};
        float rowHeight = 20;

        // Draw background
        contentStream.setNonStrokingColor(220/255f, 220/255f, 220/255f);
        contentStream.addRect(xStart, yPosition - rowHeight, tableWidth, rowHeight);
        contentStream.fill();

        // Draw text - each cell independently
        contentStream.setNonStrokingColor(0, 0, 0);
        contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), HEADER_FONT_SIZE);

        float xPosition = xStart + 5;
        float yText = yPosition - 14;

        for (int i = 0; i < headers.length; i++) {
            contentStream.beginText();
            contentStream.newLineAtOffset(xPosition, yText);
            contentStream.showText(headers[i]);
            contentStream.endText();
            xPosition += columnWidths[i] * tableWidth;
        }

        // Draw border
        contentStream.setStrokingColor(0, 0, 0);
        contentStream.addRect(xStart, yPosition - rowHeight, tableWidth, rowHeight);
        contentStream.stroke();

        return yPosition - rowHeight;
    }

    /**
     * Draw table data row.
     */
    private float drawTableDataRow(PDPageContentStream contentStream, float yPosition, float xStart, float[] columnWidths, float tableWidth, DailyReportEntry entry, float rowHeight) throws IOException {
        String[] values = {
                entry.date().format(DATE_FORMATTER),
                entry.startTime() != null ? entry.startTime().format(TIME_FORMATTER) : "-",
                String.valueOf(entry.breakMinutes()),
                entry.endTime() != null ? entry.endTime().format(TIME_FORMATTER) : "-",
                entry.totalHours() != null ? formatHours(entry.totalHours()) : "-",
                entry.overtime() != null ? formatHours(entry.overtime()) : "-"
        };

        // Draw background color based on day type
        float[] bgColor = getBackgroundColor(entry.dayType());
        if (bgColor != null) {
            contentStream.setNonStrokingColor(bgColor[0], bgColor[1], bgColor[2]);
            contentStream.addRect(xStart, yPosition - rowHeight, tableWidth, rowHeight);
            contentStream.fill();
        }

        // Draw border
        contentStream.setStrokingColor(200/255f, 200/255f, 200/255f);
        contentStream.addRect(xStart, yPosition - rowHeight, tableWidth, rowHeight);
        contentStream.stroke();

        // Reset to black for text
        contentStream.setNonStrokingColor(0, 0, 0);
        contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), NORMAL_FONT_SIZE);

        float xPosition = xStart + 5;
        float yText = yPosition - 12;

        for (int i = 0; i < values.length; i++) {
            contentStream.beginText();
            contentStream.newLineAtOffset(xPosition, yText);
            contentStream.showText(values[i]);
            contentStream.endText();
            xPosition += columnWidths[i] * tableWidth;
        }

        return yPosition - rowHeight;
    }

    /**
     * Get background color for a day type.
     * Returns RGB values as float array [r, g, b] in range 0.0-1.0.
     * Returns null for regular days (no background color).
     */
    private float[] getBackgroundColor(DayType dayType) {
        if (dayType == null) {
            return null;
        }

        return switch (dayType) {
            case WEEKEND -> new float[]{0.92f, 0.92f, 0.92f};        // Soft grey
            case SICK -> new float[]{1.0f, 0.9f, 0.9f};              // Soft red
            case VACATION -> new float[]{0.9f, 1.0f, 0.9f};          // Soft green
            case PUBLIC_HOLIDAY -> new float[]{0.95f, 0.95f, 1.0f};  // Soft blue
            case REGULAR -> null;                                     // No background
        };
    }

    /**
     * Add summary section with totals.
     */
    private void addSummary(PDPageContentStream contentStream, float yPosition, List<DailyReportEntry> entries) throws IOException {
        // Calculate totals
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

        yPosition -= 25;

        // Summary title
        contentStream.beginText();
        contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 11);
        contentStream.newLineAtOffset(MARGIN, yPosition);
        contentStream.showText("Zusammenfassung:");
        contentStream.endText();

        yPosition -= 18;

        // Summary lines
        contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);

        contentStream.beginText();
        contentStream.newLineAtOffset(MARGIN, yPosition);
        contentStream.showText(String.format("Gesamtstunden: %s", formatHours(totalHours)));
        contentStream.endText();

        yPosition -= 15;

        contentStream.beginText();
        contentStream.newLineAtOffset(MARGIN, yPosition);
        contentStream.showText(String.format("Sollstunden: %s", formatHours(totalExpectedHours)));
        contentStream.endText();

        yPosition -= 15;

        contentStream.beginText();
        contentStream.newLineAtOffset(MARGIN, yPosition);
        contentStream.showText(String.format("Gesamtuberstunden: %s", formatHoursWithSign(totalOvertime)));
        contentStream.endText();

        // Timestamp
        yPosition -= 40;

        contentStream.beginText();
        contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE), SMALL_FONT_SIZE);
        String timestamp = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").format(java.time.LocalDateTime.now());
        float timestampX = 515 - (timestamp.length() * 3); // Right align approximately
        contentStream.newLineAtOffset(timestampX, yPosition);
        contentStream.showText("Erstellt am: " + timestamp);
        contentStream.endText();
    }

    /**
     * Format hours as decimal with 2 decimal places.
     */
    private String formatHours(Double hours) {
        if (hours == null) {
            return "-";
        }
        return String.format("%.2f", hours);
    }

    /**
     * Format hours with sign (+ or -) for overtime display.
     */
    private String formatHoursWithSign(Double hours) {
        if (hours == null) {
            return "-";
        }
        String sign = hours >= 0 ? "+" : "";
        return String.format("%s%.2f", sign, hours);
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
