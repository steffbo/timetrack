package domain

import (
	"testing"
	"time"
)

func workingDaysJavaCase(
	t *testing.T,
	start string,
	end string,
	halfDays bool,
	holidays []string,
	want float64,
) {
	t.Helper()
	parseDate := func(value string) time.Time {
		date, err := time.Parse(time.DateOnly, value)
		if err != nil {
			t.Fatal(err)
		}
		return date
	}
	input := WorkingDayInput{
		WorkingWeekdays: map[time.Weekday]bool{
			time.Monday:    true,
			time.Tuesday:   true,
			time.Wednesday: true,
			time.Thursday:  true,
			time.Friday:    true,
		},
		Holidays:        map[string]bool{},
		RecurringOff:    map[string]bool{},
		HalfDaysEnabled: halfDays,
	}
	for _, holiday := range holidays {
		input.Holidays[holiday] = true
	}
	if got := WorkingDays(parseDate(start), parseDate(end), input); got != want {
		t.Fatalf("working days = %v, want %v", got, want)
	}
}

func TestWorkingDaysCalculatorDecember24AsHalfDay(t *testing.T) {
	workingDaysJavaCase(
		t,
		"2025-12-23",
		"2025-12-27",
		true,
		[]string{"2025-12-25", "2025-12-26"},
		1.5,
	)
}

func TestWorkingDaysCalculatorDecember31AsHalfDay(t *testing.T) {
	workingDaysJavaCase(
		t,
		"2025-12-30",
		"2026-01-02",
		true,
		[]string{"2026-01-01"},
		2.5,
	)
}

func TestWorkingDaysCalculatorOnlyDecember24(t *testing.T) {
	workingDaysJavaCase(t, "2025-12-24", "2025-12-24", true, nil, 0.5)
}

func TestWorkingDaysCalculatorOnlyDecember31(t *testing.T) {
	workingDaysJavaCase(t, "2025-12-31", "2025-12-31", true, nil, 0.5)
}

func TestWorkingDaysCalculatorHalfDayHolidaysDisabled(t *testing.T) {
	workingDaysJavaCase(
		t,
		"2025-12-23",
		"2025-12-27",
		false,
		[]string{"2025-12-25", "2025-12-26"},
		2,
	)
}

func TestWorkingDaysCalculatorDecember24OnWeekend(t *testing.T) {
	workingDaysJavaCase(t, "2022-12-24", "2022-12-24", true, nil, 0)
}

func TestWorkingDaysCalculatorDecember31OnWeekend(t *testing.T) {
	workingDaysJavaCase(t, "2023-12-31", "2023-12-31", true, nil, 0)
}

func TestWorkingDaysCalculatorDecember24AsPublicHoliday(t *testing.T) {
	workingDaysJavaCase(
		t,
		"2025-12-24",
		"2025-12-24",
		true,
		[]string{"2025-12-24"},
		0,
	)
}

func TestWorkingDaysCalculatorBothHalfDayHolidaysInSamePeriod(t *testing.T) {
	workingDaysJavaCase(
		t,
		"2025-12-22",
		"2025-12-31",
		true,
		[]string{"2025-12-25", "2025-12-26"},
		5,
	)
}

func TestWorkingDaysCalculatorRegularWeekWithoutHalfDayHolidays(t *testing.T) {
	workingDaysJavaCase(t, "2025-10-20", "2025-10-24", true, nil, 5)
}

func TestWorkingDaysCalculatorEmptyDateRange(t *testing.T) {
	workingDaysJavaCase(t, "2025-12-31", "2025-12-24", true, nil, 0)
}
