package publicholidays

import (
	"testing"
	"time"

	"github.com/steffbo/timetrack/backend/internal/domain"
)

func has(t *testing.T, days []time.Time, date string, want bool) {
	t.Helper()
	got := false
	for _, d := range days {
		if d.Format(time.DateOnly) == date {
			got = true
		}
	}
	if got != want {
		t.Fatal(date, got, want)
	}
}

// Public holiday calculation cases do not require database reads.
func TestShouldGetPublicHolidaysForBerlin(t *testing.T) {
	days := (domain.GermanPublicHolidays{}).GetPublicHolidays(2025, domain.Berlin)
	if len(days) < 10 {
		t.Fatal(days)
	}
	for _, d := range []string{
		"2025-01-01", "2025-05-01", "2025-10-03", "2025-12-25", "2025-12-26", "2025-03-08",
	} {
		has(t, days, d, true)
	}
}
func TestShouldGetPublicHolidaysForBrandenburg(t *testing.T) {
	days := (domain.GermanPublicHolidays{}).GetPublicHolidays(2025, domain.Brandenburg)
	if len(days) == 0 {
		t.Fatal(days)
	}
	has(t, days, "2025-10-31", true)
	has(t, days, "2025-03-08", false)
}
func TestShouldIncludeEasterBasedMovableHolidays(t *testing.T) {
	days := (domain.GermanPublicHolidays{}).GetPublicHolidays(2025, domain.Berlin)
	for _, d := range []string{"2025-04-18", "2025-04-21", "2025-05-29", "2025-06-09"} {
		has(t, days, d, true)
	}
}
func TestShouldCheckIfDateIsPublicHoliday(t *testing.T) {
	for _, tc := range []struct {
		date  string
		state domain.GermanState
		want  bool
	}{
		{"2025-01-01", domain.Berlin, true}, {"2025-03-08", domain.Berlin, true},
		{"2025-03-08", domain.Brandenburg, false}, {"2025-01-15", domain.Berlin, false},
	} {
		d, err := time.Parse(time.DateOnly, tc.date)
		if err != nil {
			t.Fatal(err)
		}
		if got := (domain.GermanPublicHolidays{}).IsPublicHoliday(d, tc.state); got != tc.want {
			t.Fatal(tc, got)
		}
	}
}
func TestShouldCalculateHolidaysCorrectlyForDifferentYears(t *testing.T) {
	for _, year := range []int{2024, 2025, 2026} {
		days := (domain.GermanPublicHolidays{}).GetPublicHolidays(year, domain.Berlin)
		if len(days) == 0 {
			t.Fatal(year)
		}
		for _, d := range days {
			if d.Year() != year {
				t.Fatal(d)
			}
		}
	}
}
func TestAllYearsStatesSortedNames(t *testing.T) {
	out := All().HolidaysByYearAndState
	if len(out) != 5 {
		t.Fatal(out)
	}
	for year, states := range out {
		if len(states) != 2 {
			t.Fatal(year, states)
		}
		for state, days := range states {
			if len(days) != 10 {
				t.Fatal(year, state, days)
			}
			for i, d := range days {
				if d.Name == "" || (i > 0 && !d.Date.After(days[i-1].Date.Time)) {
					t.Fatal(d)
				}
				key := d.Date.Format("01-02")
				if d.IsStateSpecific != (key == "03-08" || key == "10-31") {
					t.Fatal(d)
				}
			}
		}
	}
}
