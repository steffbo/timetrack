package cc.remer.timetrack.util;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Utility class for common validation logic across use cases.
 * Centralizes validation to ensure consistency and reduce code duplication.
 */
public final class ValidationUtils {

    private ValidationUtils() {
        // Private constructor to prevent instantiation
    }

    /**
     * Validates that a date range is valid (end date is not before start date).
     *
     * @param startDate the start date (required)
     * @param endDate the end date (required)
     * @param errorMessage the error message to throw if validation fails
     * @throws IllegalArgumentException if start date is null, end date is null, or end date is before start date
     */
    public static void validateDateRange(LocalDate startDate, LocalDate endDate, String errorMessage) {
        if (startDate == null) {
            throw new IllegalArgumentException("Startdatum ist erforderlich");
        }
        if (endDate == null) {
            throw new IllegalArgumentException("Enddatum ist erforderlich");
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException(errorMessage);
        }
    }

    /**
     * Validates that a date range is valid with default error message.
     *
     * @param startDate the start date (required)
     * @param endDate the end date (required)
     * @throws IllegalArgumentException if validation fails
     */
    public static void validateDateRange(LocalDate startDate, LocalDate endDate) {
        validateDateRange(startDate, endDate, "Enddatum muss nach dem Startdatum liegen");
    }

    /**
     * Validates that a date range is valid, allowing null end date.
     *
     * @param startDate the start date (required)
     * @param endDate the end date (optional)
     * @param errorMessage the error message to throw if validation fails
     * @throws IllegalArgumentException if validation fails
     */
    public static void validateOptionalEndDate(LocalDate startDate, LocalDate endDate, String errorMessage) {
        if (startDate == null) {
            throw new IllegalArgumentException("Startdatum ist erforderlich");
        }
        if (endDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException(errorMessage);
        }
    }

    /**
     * Validates that a weekday is in valid range (1-7, Monday to Sunday).
     *
     * @param weekday the weekday to validate
     * @throws IllegalArgumentException if weekday is not between 1 and 7
     */
    public static void validateWeekday(Integer weekday) {
        if (weekday == null || weekday < 1 || weekday > 7) {
            throw new IllegalArgumentException("Wochentag muss zwischen 1 (Montag) und 7 (Sonntag) liegen");
        }
    }

    /**
     * Validates that hours are in valid range (0-24).
     *
     * @param hours the hours to validate
     * @throws IllegalArgumentException if hours are null or not between 0 and 24
     */
    public static void validateHours(Double hours) {
        if (hours == null || hours < 0 || hours > 24) {
            throw new IllegalArgumentException("Stunden müssen zwischen 0 und 24 liegen");
        }
    }

    /**
     * Validates that hours are non-negative, allowing 0.
     *
     * @param hours the hours to validate
     * @throws IllegalArgumentException if hours are null or negative
     */
    public static void validateNonNegativeHours(Double hours) {
        if (hours == null) {
            throw new IllegalArgumentException("Stunden dürfen nicht null sein");
        }
        if (hours < 0) {
            throw new IllegalArgumentException("Stunden pro Tag dürfen nicht negativ sein");
        }
    }

    /**
     * Validates that a time range is valid (end time is after start time).
     *
     * @param startTime the start time (required)
     * @param endTime the end time (required)
     * @throws IllegalArgumentException if start time is null, end time is null, or end time is not after start time
     */
    public static void validateTimeRange(LocalTime startTime, LocalTime endTime) {
        if (startTime == null) {
            throw new IllegalArgumentException("Startzeit ist erforderlich");
        }
        if (endTime == null) {
            throw new IllegalArgumentException("Endzeit ist erforderlich");
        }
        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("Endzeit muss nach Startzeit liegen");
        }
    }

    /**
     * Validates that week interval is valid (at least 1).
     *
     * @param weekInterval the week interval to validate
     * @throws IllegalArgumentException if week interval is null or less than 1
     */
    public static void validateWeekInterval(Integer weekInterval) {
        if (weekInterval == null || weekInterval < 1) {
            throw new IllegalArgumentException("Wochenintervall ist erforderlich und muss mindestens 1 sein");
        }
    }

    /**
     * Validates that week of month is in valid range (1-5).
     *
     * @param weekOfMonth the week of month to validate
     * @throws IllegalArgumentException if week of month is null or not between 1 and 5
     */
    public static void validateWeekOfMonth(Integer weekOfMonth) {
        if (weekOfMonth == null || weekOfMonth < 1 || weekOfMonth > 5) {
            throw new IllegalArgumentException("Woche des Monats muss zwischen 1 und 5 liegen");
        }
    }

    /**
     * Validates that a required field is not null.
     *
     * @param value the value to check
     * @param fieldName the name of the field (for error message)
     * @throws IllegalArgumentException if value is null
     */
    public static void validateRequired(Object value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " ist erforderlich");
        }
    }
}
