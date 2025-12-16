package cc.remer.timetrack.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for MapperUtils.
 */
class MapperUtilsTest {

    @Test
    @DisplayName("Constructor should throw UnsupportedOperationException")
    void constructor_shouldThrowException() {
        assertThatThrownBy(() -> {
            var constructor = MapperUtils.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            constructor.newInstance();
        }).hasCauseInstanceOf(UnsupportedOperationException.class)
          .hasRootCauseMessage("Utility class cannot be instantiated");
    }

    // ==================== toOffsetDateTime (UTC) Tests ====================

    @Test
    @DisplayName("toOffsetDateTime should convert LocalDateTime to OffsetDateTime in UTC")
    void toOffsetDateTime_shouldConvertToUtc() {
        // Given
        LocalDateTime timestamp = LocalDateTime.of(2025, 12, 16, 14, 30, 45);

        // When
        OffsetDateTime result = MapperUtils.toOffsetDateTime(timestamp);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getYear()).isEqualTo(2025);
        assertThat(result.getMonthValue()).isEqualTo(12);
        assertThat(result.getDayOfMonth()).isEqualTo(16);
        assertThat(result.getHour()).isEqualTo(14);
        assertThat(result.getMinute()).isEqualTo(30);
        assertThat(result.getSecond()).isEqualTo(45);
        assertThat(result.getOffset()).isEqualTo(ZoneOffset.UTC);
    }

    @Test
    @DisplayName("toOffsetDateTime should return null when input is null")
    void toOffsetDateTime_shouldReturnNull_whenInputIsNull() {
        // When
        OffsetDateTime result = MapperUtils.toOffsetDateTime(null);

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("toOffsetDateTime should handle midnight timestamp")
    void toOffsetDateTime_shouldHandleMidnight() {
        // Given
        LocalDateTime timestamp = LocalDateTime.of(2025, 1, 1, 0, 0, 0);

        // When
        OffsetDateTime result = MapperUtils.toOffsetDateTime(timestamp);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getHour()).isEqualTo(0);
        assertThat(result.getMinute()).isEqualTo(0);
        assertThat(result.getSecond()).isEqualTo(0);
        assertThat(result.getOffset()).isEqualTo(ZoneOffset.UTC);
    }

    @Test
    @DisplayName("toOffsetDateTime should handle end of day timestamp")
    void toOffsetDateTime_shouldHandleEndOfDay() {
        // Given
        LocalDateTime timestamp = LocalDateTime.of(2025, 12, 31, 23, 59, 59);

        // When
        OffsetDateTime result = MapperUtils.toOffsetDateTime(timestamp);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getHour()).isEqualTo(23);
        assertThat(result.getMinute()).isEqualTo(59);
        assertThat(result.getSecond()).isEqualTo(59);
        assertThat(result.getOffset()).isEqualTo(ZoneOffset.UTC);
    }

    @Test
    @DisplayName("toOffsetDateTime should preserve nanoseconds")
    void toOffsetDateTime_shouldPreserveNanoseconds() {
        // Given
        LocalDateTime timestamp = LocalDateTime.of(2025, 6, 15, 12, 30, 45, 123456789);

        // When
        OffsetDateTime result = MapperUtils.toOffsetDateTime(timestamp);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getNano()).isEqualTo(123456789);
        assertThat(result.getOffset()).isEqualTo(ZoneOffset.UTC);
    }

    // ==================== toOffsetDateTime (Custom Zone) Tests ====================

    @Test
    @DisplayName("toOffsetDateTime with custom zone should convert with specified offset")
    void toOffsetDateTime_shouldConvertWithCustomZone() {
        // Given
        LocalDateTime timestamp = LocalDateTime.of(2025, 12, 16, 14, 30, 45);
        ZoneOffset customOffset = ZoneOffset.ofHours(5);

        // When
        OffsetDateTime result = MapperUtils.toOffsetDateTime(timestamp, customOffset);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getYear()).isEqualTo(2025);
        assertThat(result.getHour()).isEqualTo(14);
        assertThat(result.getOffset()).isEqualTo(customOffset);
    }

    @Test
    @DisplayName("toOffsetDateTime with custom zone should return null when input is null")
    void toOffsetDateTime_withCustomZone_shouldReturnNull_whenInputIsNull() {
        // Given
        ZoneOffset customOffset = ZoneOffset.ofHours(2);

        // When
        OffsetDateTime result = MapperUtils.toOffsetDateTime(null, customOffset);

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("toOffsetDateTime should handle negative zone offset")
    void toOffsetDateTime_shouldHandleNegativeZoneOffset() {
        // Given
        LocalDateTime timestamp = LocalDateTime.of(2025, 7, 4, 10, 0, 0);
        ZoneOffset negativeOffset = ZoneOffset.ofHours(-5);

        // When
        OffsetDateTime result = MapperUtils.toOffsetDateTime(timestamp, negativeOffset);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getHour()).isEqualTo(10);
        assertThat(result.getOffset()).isEqualTo(negativeOffset);
    }

    @Test
    @DisplayName("toOffsetDateTime should handle zero offset (UTC)")
    void toOffsetDateTime_shouldHandleZeroOffset() {
        // Given
        LocalDateTime timestamp = LocalDateTime.of(2025, 3, 20, 15, 45, 0);
        ZoneOffset zeroOffset = ZoneOffset.ofHours(0);

        // When
        OffsetDateTime result = MapperUtils.toOffsetDateTime(timestamp, zeroOffset);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(result.getOffset()).isEqualTo(zeroOffset);
    }

    @Test
    @DisplayName("toOffsetDateTime should handle fractional zone offset")
    void toOffsetDateTime_shouldHandleFractionalZoneOffset() {
        // Given
        LocalDateTime timestamp = LocalDateTime.of(2025, 8, 10, 9, 15, 30);
        ZoneOffset fractionalOffset = ZoneOffset.ofHoursMinutes(5, 30); // India Standard Time

        // When
        OffsetDateTime result = MapperUtils.toOffsetDateTime(timestamp, fractionalOffset);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getOffset()).isEqualTo(fractionalOffset);
        assertThat(result.getOffset().getTotalSeconds()).isEqualTo(19800); // 5.5 hours in seconds
    }

    @Test
    @DisplayName("toOffsetDateTime with custom zone should preserve nanoseconds")
    void toOffsetDateTime_withCustomZone_shouldPreserveNanoseconds() {
        // Given
        LocalDateTime timestamp = LocalDateTime.of(2025, 11, 5, 16, 20, 10, 987654321);
        ZoneOffset customOffset = ZoneOffset.ofHours(-8);

        // When
        OffsetDateTime result = MapperUtils.toOffsetDateTime(timestamp, customOffset);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getNano()).isEqualTo(987654321);
        assertThat(result.getOffset()).isEqualTo(customOffset);
    }

    // ==================== Edge Case Tests ====================

    @Test
    @DisplayName("toOffsetDateTime should handle leap year date")
    void toOffsetDateTime_shouldHandleLeapYear() {
        // Given - Feb 29, 2024 is a valid leap year date
        LocalDateTime timestamp = LocalDateTime.of(2024, 2, 29, 12, 0, 0);

        // When
        OffsetDateTime result = MapperUtils.toOffsetDateTime(timestamp);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getMonthValue()).isEqualTo(2);
        assertThat(result.getDayOfMonth()).isEqualTo(29);
        assertThat(result.getYear()).isEqualTo(2024);
    }

    @Test
    @DisplayName("toOffsetDateTime should handle year boundaries")
    void toOffsetDateTime_shouldHandleYearBoundaries() {
        // Given
        LocalDateTime newYear = LocalDateTime.of(2025, 1, 1, 0, 0, 0);
        LocalDateTime oldYear = LocalDateTime.of(2024, 12, 31, 23, 59, 59);

        // When
        OffsetDateTime newYearResult = MapperUtils.toOffsetDateTime(newYear);
        OffsetDateTime oldYearResult = MapperUtils.toOffsetDateTime(oldYear);

        // Then
        assertThat(newYearResult).isNotNull();
        assertThat(newYearResult.getYear()).isEqualTo(2025);
        assertThat(oldYearResult).isNotNull();
        assertThat(oldYearResult.getYear()).isEqualTo(2024);
    }

    @Test
    @DisplayName("Multiple calls with same input should produce equal results")
    void toOffsetDateTime_shouldProduceConsistentResults() {
        // Given
        LocalDateTime timestamp = LocalDateTime.of(2025, 5, 15, 10, 30, 0);

        // When
        OffsetDateTime result1 = MapperUtils.toOffsetDateTime(timestamp);
        OffsetDateTime result2 = MapperUtils.toOffsetDateTime(timestamp);

        // Then
        assertThat(result1).isEqualTo(result2);
    }
}
