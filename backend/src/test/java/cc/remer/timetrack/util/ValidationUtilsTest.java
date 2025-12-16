package cc.remer.timetrack.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Unit tests for ValidationUtils.
 */
@DisplayName("ValidationUtils Tests")
class ValidationUtilsTest {

    // ===== Date Range Validation Tests =====

    @Test
    @DisplayName("Should accept valid date range")
    void shouldAcceptValidDateRange() {
        LocalDate start = LocalDate.of(2025, 1, 1);
        LocalDate end = LocalDate.of(2025, 1, 10);

        assertThatCode(() -> ValidationUtils.validateDateRange(start, end))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should accept same start and end date")
    void shouldAcceptSameStartAndEndDate() {
        LocalDate date = LocalDate.of(2025, 1, 1);

        assertThatCode(() -> ValidationUtils.validateDateRange(date, date))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should reject null start date")
    void shouldRejectNullStartDate() {
        LocalDate end = LocalDate.of(2025, 1, 10);

        assertThatThrownBy(() -> ValidationUtils.validateDateRange(null, end))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Startdatum ist erforderlich");
    }

    @Test
    @DisplayName("Should reject null end date")
    void shouldRejectNullEndDate() {
        LocalDate start = LocalDate.of(2025, 1, 1);

        assertThatThrownBy(() -> ValidationUtils.validateDateRange(start, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Enddatum ist erforderlich");
    }

    @Test
    @DisplayName("Should reject end date before start date")
    void shouldRejectEndDateBeforeStartDate() {
        LocalDate start = LocalDate.of(2025, 1, 10);
        LocalDate end = LocalDate.of(2025, 1, 1);

        assertThatThrownBy(() -> ValidationUtils.validateDateRange(start, end))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Enddatum muss nach dem Startdatum liegen");
    }

    @Test
    @DisplayName("Should use custom error message for date range")
    void shouldUseCustomErrorMessageForDateRange() {
        LocalDate start = LocalDate.of(2025, 1, 10);
        LocalDate end = LocalDate.of(2025, 1, 1);
        String customMessage = "Custom error message";

        assertThatThrownBy(() -> ValidationUtils.validateDateRange(start, end, customMessage))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(customMessage);
    }

    @Test
    @DisplayName("Should accept null end date when optional")
    void shouldAcceptNullEndDateWhenOptional() {
        LocalDate start = LocalDate.of(2025, 1, 1);

        assertThatCode(() -> ValidationUtils.validateOptionalEndDate(start, null, "Error"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should reject end date before start date when optional")
    void shouldRejectEndDateBeforeStartDateWhenOptional() {
        LocalDate start = LocalDate.of(2025, 1, 10);
        LocalDate end = LocalDate.of(2025, 1, 1);

        assertThatThrownBy(() -> ValidationUtils.validateOptionalEndDate(start, end, "Error"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Error");
    }

    // ===== Weekday Validation Tests =====

    @Test
    @DisplayName("Should accept valid weekday Monday (1)")
    void shouldAcceptValidWeekdayMonday() {
        assertThatCode(() -> ValidationUtils.validateWeekday(1))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should accept valid weekday Sunday (7)")
    void shouldAcceptValidWeekdaySunday() {
        assertThatCode(() -> ValidationUtils.validateWeekday(7))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should reject null weekday")
    void shouldRejectNullWeekday() {
        assertThatThrownBy(() -> ValidationUtils.validateWeekday(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Wochentag muss zwischen 1");
    }

    @Test
    @DisplayName("Should reject weekday less than 1")
    void shouldRejectWeekdayLessThan1() {
        assertThatThrownBy(() -> ValidationUtils.validateWeekday(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Wochentag muss zwischen 1");
    }

    @Test
    @DisplayName("Should reject weekday greater than 7")
    void shouldRejectWeekdayGreaterThan7() {
        assertThatThrownBy(() -> ValidationUtils.validateWeekday(8))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Wochentag muss zwischen 1");
    }

    // ===== Hours Validation Tests =====

    @Test
    @DisplayName("Should accept valid hours (8.0)")
    void shouldAcceptValidHours() {
        assertThatCode(() -> ValidationUtils.validateHours(8.0))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should accept zero hours")
    void shouldAcceptZeroHours() {
        assertThatCode(() -> ValidationUtils.validateHours(0.0))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should accept 24 hours")
    void shouldAccept24Hours() {
        assertThatCode(() -> ValidationUtils.validateHours(24.0))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should reject null hours")
    void shouldRejectNullHours() {
        assertThatThrownBy(() -> ValidationUtils.validateHours(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Stunden müssen zwischen 0 und 24 liegen");
    }

    @Test
    @DisplayName("Should reject negative hours")
    void shouldRejectNegativeHours() {
        assertThatThrownBy(() -> ValidationUtils.validateHours(-1.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Stunden müssen zwischen 0 und 24 liegen");
    }

    @Test
    @DisplayName("Should reject hours greater than 24")
    void shouldRejectHoursGreaterThan24() {
        assertThatThrownBy(() -> ValidationUtils.validateHours(25.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Stunden müssen zwischen 0 und 24 liegen");
    }

    // ===== Non-Negative Hours Validation Tests =====

    @Test
    @DisplayName("Should accept valid non-negative hours")
    void shouldAcceptValidNonNegativeHours() {
        assertThatCode(() -> ValidationUtils.validateNonNegativeHours(8.5))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should accept zero for non-negative hours")
    void shouldAcceptZeroForNonNegativeHours() {
        assertThatCode(() -> ValidationUtils.validateNonNegativeHours(0.0))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should reject null for non-negative hours")
    void shouldRejectNullForNonNegativeHours() {
        assertThatThrownBy(() -> ValidationUtils.validateNonNegativeHours(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Stunden dürfen nicht null sein");
    }

    @Test
    @DisplayName("Should reject negative for non-negative hours")
    void shouldRejectNegativeForNonNegativeHours() {
        assertThatThrownBy(() -> ValidationUtils.validateNonNegativeHours(-0.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Stunden pro Tag dürfen nicht negativ sein");
    }

    // ===== Time Range Validation Tests =====

    @Test
    @DisplayName("Should accept valid time range")
    void shouldAcceptValidTimeRange() {
        LocalTime start = LocalTime.of(9, 0);
        LocalTime end = LocalTime.of(17, 0);

        assertThatCode(() -> ValidationUtils.validateTimeRange(start, end))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should reject null start time")
    void shouldRejectNullStartTime() {
        LocalTime end = LocalTime.of(17, 0);

        assertThatThrownBy(() -> ValidationUtils.validateTimeRange(null, end))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Startzeit ist erforderlich");
    }

    @Test
    @DisplayName("Should reject null end time")
    void shouldRejectNullEndTime() {
        LocalTime start = LocalTime.of(9, 0);

        assertThatThrownBy(() -> ValidationUtils.validateTimeRange(start, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Endzeit ist erforderlich");
    }

    @Test
    @DisplayName("Should reject end time before start time")
    void shouldRejectEndTimeBeforeStartTime() {
        LocalTime start = LocalTime.of(17, 0);
        LocalTime end = LocalTime.of(9, 0);

        assertThatThrownBy(() -> ValidationUtils.validateTimeRange(start, end))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Endzeit muss nach Startzeit liegen");
    }

    @Test
    @DisplayName("Should reject same start and end time")
    void shouldRejectSameStartAndEndTime() {
        LocalTime time = LocalTime.of(9, 0);

        assertThatThrownBy(() -> ValidationUtils.validateTimeRange(time, time))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Endzeit muss nach Startzeit liegen");
    }

    // ===== Week Interval Validation Tests =====

    @Test
    @DisplayName("Should accept valid week interval")
    void shouldAcceptValidWeekInterval() {
        assertThatCode(() -> ValidationUtils.validateWeekInterval(2))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should accept week interval of 1")
    void shouldAcceptWeekIntervalOf1() {
        assertThatCode(() -> ValidationUtils.validateWeekInterval(1))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should reject null week interval")
    void shouldRejectNullWeekInterval() {
        assertThatThrownBy(() -> ValidationUtils.validateWeekInterval(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Wochenintervall");
    }

    @Test
    @DisplayName("Should reject week interval less than 1")
    void shouldRejectWeekIntervalLessThan1() {
        assertThatThrownBy(() -> ValidationUtils.validateWeekInterval(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Wochenintervall");
    }

    // ===== Week of Month Validation Tests =====

    @Test
    @DisplayName("Should accept valid week of month (1)")
    void shouldAcceptValidWeekOfMonth1() {
        assertThatCode(() -> ValidationUtils.validateWeekOfMonth(1))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should accept valid week of month (5)")
    void shouldAcceptValidWeekOfMonth5() {
        assertThatCode(() -> ValidationUtils.validateWeekOfMonth(5))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should reject null week of month")
    void shouldRejectNullWeekOfMonth() {
        assertThatThrownBy(() -> ValidationUtils.validateWeekOfMonth(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Woche des Monats");
    }

    @Test
    @DisplayName("Should reject week of month less than 1")
    void shouldRejectWeekOfMonthLessThan1() {
        assertThatThrownBy(() -> ValidationUtils.validateWeekOfMonth(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Woche des Monats");
    }

    @Test
    @DisplayName("Should reject week of month greater than 5")
    void shouldRejectWeekOfMonthGreaterThan5() {
        assertThatThrownBy(() -> ValidationUtils.validateWeekOfMonth(6))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Woche des Monats");
    }

    // ===== Required Field Validation Tests =====

    @Test
    @DisplayName("Should accept non-null required field")
    void shouldAcceptNonNullRequiredField() {
        assertThatCode(() -> ValidationUtils.validateRequired("value", "Field"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should reject null required field")
    void shouldRejectNullRequiredField() {
        assertThatThrownBy(() -> ValidationUtils.validateRequired(null, "Testfeld"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Testfeld ist erforderlich");
    }
}
