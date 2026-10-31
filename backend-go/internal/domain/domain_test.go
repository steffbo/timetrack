package domain

import (
	"testing"
	"time"
)

func TestHolidays(t *testing.T) {
	h := GermanPublicHolidays{}
	for _, state := range []GermanState{Berlin, Brandenburg} {
		days := h.GetPublicHolidays(2025, state)
		if len(days) != 10 {
			t.Fatal(days)
		}
		for _, d := range []string{"2025-01-01", "2025-04-18", "2025-04-21", "2025-05-29", "2025-06-09"} {
			date, _ := time.Parse(time.DateOnly, d)
			if !h.IsPublicHoliday(date, state) {
				t.Fatal(d, state)
			}
		}
	}
	for _, tc := range []struct {
		date                string
		berlin, brandenburg bool
	}{
		{"2025-03-08", true, false}, {"2025-10-31", false, true}, {"2025-04-20", false, false},
		{"2025-12-24", false, false}, {"2025-12-31", false, false}} {
		d, _ := time.Parse(time.DateOnly, tc.date)
		if h.IsPublicHoliday(d, Berlin) != tc.berlin || h.IsPublicHoliday(d, Brandenburg) != tc.brandenburg {
			t.Fatal(tc)
		}
	}
}
func TestPrecedence(t *testing.T) {
	chain := []DayTypePrecedence{DayWeekend, DayPublicHoliday, DaySick, DayPersonal, DayRecurringOffDay, DayVacation, DayWork, DayNoEntry}
	for i := 1; i < len(chain); i++ {
		if !chain[i-1].HasHigherPriorityThan(chain[i]) {
			t.Fatal(chain)
		}
	}
	if DayEducation.Priority() != DayPersonal.Priority() {
		t.Fatal("education priority")
	}
}
