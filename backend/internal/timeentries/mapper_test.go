package timeentries

import (
	"testing"
	"time"
)

func TestLocalOffsetDSTResolution(t *testing.T) {
	old := time.Local
	berlin, err := time.LoadLocation("Europe/Berlin")
	if err != nil {
		t.Fatal(err)
	}
	time.Local = berlin
	t.Cleanup(func() { time.Local = old })
	for _, tc := range []struct{ wall, want string }{
		{"2025-10-26T02:30:00Z", "2025-10-26T02:30:00+02:00"},
		{"2025-03-30T02:30:00Z", "2025-03-30T03:30:00+02:00"},
		{"2025-01-15T08:00:00Z", "2025-01-15T08:00:00+01:00"},
		{"2025-07-15T08:00:00Z", "2025-07-15T08:00:00+02:00"},
	} {
		wall, err := time.Parse(time.RFC3339, tc.wall)
		if err != nil {
			t.Fatal(err)
		}
		got := localOffset(wall).Format(time.RFC3339)
		if got != tc.want {
			t.Fatal(tc, got)
		}
	}
}
func TestHoursTruncateWholeMinutesBeforeBreaks(t *testing.T) {
	in := time.Date(2025, 1, 1, 8, 0, 0, 0, time.UTC)
	out := in.Add(8*time.Hour + 59*time.Second)
	e := Entry{ClockIn: in, ClockOut: &out, BreakMinutes: 30}
	if got := *e.HoursWorked(); got != 7.5 {
		t.Fatal(got)
	}
}
func TestLocalDateTimeDiagnosticFormatting(t *testing.T) {
	for _, tc := range []struct {
		nano int
		want string
	}{
		{0, "2025-01-01T08:00"}, {123000000, "2025-01-01T08:00:00.123"},
		{123400000, "2025-01-01T08:00:00.123400"}, {123400001, "2025-01-01T08:00:00.123400001"},
	} {
		got := formatDateTime(time.Date(2025, 1, 1, 8, 0, 0, tc.nano, time.UTC))
		if got != tc.want {
			t.Fatal(got, tc.want)
		}
	}
}
