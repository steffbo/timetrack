package reports

import "testing"

func TestDecimalRounding(t *testing.T) {
	for _, tc := range []struct {
		value float64
		want  string
	}{
		{.125, "0,13"}, {2.675, "2,68"}, {-.125, "-0,13"}, {0, "0,00"}, {7.5, "7,50"},
	} {
		if got := decimal(tc.value); got != tc.want {
			t.Fatal(tc, got)
		}
	}
}
func TestPDFHourAndMinuteRounding(t *testing.T) {
	for _, tc := range []struct {
		value float64
		sign  bool
		want  string
	}{
		{8.5, false, "8h 30m"}, {-.5, false, "-0h 30m"}, {-.5, true, "-30 min"},
		{0, true, "+0 min"}, {1.999, false, "2h 0m"}, {1.999, true, "+2h 0m"},
	} {
		if got := hours(tc.value, tc.sign); got != tc.want {
			t.Fatal(tc, got)
		}
	}
}
