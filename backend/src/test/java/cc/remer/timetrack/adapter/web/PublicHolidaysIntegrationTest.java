package cc.remer.timetrack.adapter.web;

import cc.remer.timetrack.adapter.persistence.RepositoryTestBase;
import cc.remer.timetrack.domain.publicholiday.GermanPublicHolidays;
import cc.remer.timetrack.domain.user.GermanState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.Month;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for German public holidays calculator.
 */
@DisplayName("Public Holidays Integration Tests")
class PublicHolidaysIntegrationTest extends RepositoryTestBase {

    @Autowired
    private GermanPublicHolidays germanPublicHolidays;

    @Test
    @DisplayName("Should get public holidays for Berlin")
    void shouldGetPublicHolidaysForBerlin() {
        // Act
        List<LocalDate> holidays = germanPublicHolidays.getPublicHolidays(2025, GermanState.BERLIN);

        // Assert
        assertThat(holidays).isNotEmpty();
        assertThat(holidays).hasSizeGreaterThanOrEqualTo(10); // Germany has at least 9 common holidays + Berlin specific

        // Check for common holidays
        assertThat(holidays).contains(LocalDate.of(2025, Month.JANUARY, 1)); // New Year
        assertThat(holidays).contains(LocalDate.of(2025, Month.MAY, 1)); // Labour Day
        assertThat(holidays).contains(LocalDate.of(2025, Month.OCTOBER, 3)); // German Unity Day
        assertThat(holidays).contains(LocalDate.of(2025, Month.DECEMBER, 25)); // Christmas
        assertThat(holidays).contains(LocalDate.of(2025, Month.DECEMBER, 26)); // Boxing Day

        // Check for Berlin-specific holiday
        assertThat(holidays).contains(LocalDate.of(2025, Month.MARCH, 8)); // International Women's Day
    }

    @Test
    @DisplayName("Should get public holidays for Brandenburg")
    void shouldGetPublicHolidaysForBrandenburg() {
        // Act
        List<LocalDate> holidays = germanPublicHolidays.getPublicHolidays(2025, GermanState.BRANDENBURG);

        // Assert
        assertThat(holidays).isNotEmpty();

        // Check for Brandenburg-specific holiday
        assertThat(holidays).contains(LocalDate.of(2025, Month.OCTOBER, 31)); // Reformation Day

        // Should NOT have Berlin-specific holiday
        assertThat(holidays).doesNotContain(LocalDate.of(2025, Month.MARCH, 8));
    }

    @Test
    @DisplayName("Should include Easter-based movable holidays")
    void shouldIncludeEasterBasedMovableHolidays() {
        // Act
        List<LocalDate> holidays = germanPublicHolidays.getPublicHolidays(2025, GermanState.BERLIN);

        // 2025 Easter is April 20
        LocalDate easterSunday = LocalDate.of(2025, Month.APRIL, 20);

        // Assert Easter-based holidays
        assertThat(holidays).contains(easterSunday.minusDays(2)); // Good Friday (April 18)
        assertThat(holidays).contains(easterSunday.plusDays(1)); // Easter Monday (April 21)
        assertThat(holidays).contains(easterSunday.plusDays(39)); // Ascension Day (May 29)
        assertThat(holidays).contains(easterSunday.plusDays(50)); // Whit Monday (June 9)
    }

    @Test
    @DisplayName("Should check if date is public holiday")
    void shouldCheckIfDateIsPublicHoliday() {
        // Assert
        assertThat(germanPublicHolidays.isPublicHoliday(LocalDate.of(2025, Month.JANUARY, 1), GermanState.BERLIN)).isTrue();
        assertThat(germanPublicHolidays.isPublicHoliday(LocalDate.of(2025, Month.MARCH, 8), GermanState.BERLIN)).isTrue();
        assertThat(germanPublicHolidays.isPublicHoliday(LocalDate.of(2025, Month.MARCH, 8), GermanState.BRANDENBURG)).isFalse();
        assertThat(germanPublicHolidays.isPublicHoliday(LocalDate.of(2025, Month.JANUARY, 15), GermanState.BERLIN)).isFalse();
    }

    @Test
    @DisplayName("Should calculate holidays correctly for different years")
    void shouldCalculateHolidaysCorrectlyForDifferentYears() {
        // Act
        List<LocalDate> holidays2024 = germanPublicHolidays.getPublicHolidays(2024, GermanState.BERLIN);
        List<LocalDate> holidays2025 = germanPublicHolidays.getPublicHolidays(2025, GermanState.BERLIN);
        List<LocalDate> holidays2026 = germanPublicHolidays.getPublicHolidays(2026, GermanState.BERLIN);

        // Assert all years have holidays
        assertThat(holidays2024).isNotEmpty();
        assertThat(holidays2025).isNotEmpty();
        assertThat(holidays2026).isNotEmpty();

        // Assert all holidays are in the correct year
        assertThat(holidays2024).allMatch(date -> date.getYear() == 2024);
        assertThat(holidays2025).allMatch(date -> date.getYear() == 2025);
        assertThat(holidays2026).allMatch(date -> date.getYear() == 2026);
    }
}
