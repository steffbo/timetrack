package domain

import (
	"testing"
	"time"
)

func TestRecurringOffDayEvaluator(t *testing.T) {
	date := func(s string) time.Time {
		v, err := time.Parse(time.DateOnly, s)
		if err != nil {
			t.Fatal(err)
		}
		return v
	}
	ref := date("2025-01-06")
	interval, last := 4, 5
	base := RecurringRule{Active: true, Pattern: EveryNthWeek, Weekday: 1, StartDate: ref,
		ReferenceDate: &ref, WeekInterval: &interval}
	for _, tc := range []struct {
		name, date   string
		change       func(*RecurringRule)
		exempt, want bool
	}{
		{"reference", "2025-01-06", nil, false, true},
		{"four weeks", "2025-02-03", nil, false, true},
		{"off interval", "2025-01-13", nil, false, false},
		{"wrong weekday", "2025-02-04", nil, false, false},
		{"before start", "2024-12-30", nil, false, false},
		{"inactive", "2025-01-06", func(r *RecurringRule) {
			r.Active = false
		}, false, false},
		{"after end", "2025-02-03", func(r *RecurringRule) {
			r.EndDate = &ref
		}, false, false},
		{"inclusive end", "2025-01-06", func(r *RecurringRule) {
			r.EndDate = &ref
		}, false, true},
		{"exemption", "2025-01-06", nil, true, false},
		{"missing reference", "2025-01-06", func(r *RecurringRule) {
			r.ReferenceDate = nil
		}, false, false},
		{"invalid interval", "2025-01-06", func(r *RecurringRule) {
			v := 0
			r.WeekInterval = &v
		}, false, false},
		{"last of four", "2025-02-24", func(r *RecurringRule) {
			r.Pattern = NthWeekdayOfMonth
			r.WeekOfMonth = &last
		}, false, true},
		{"last of five", "2025-03-31", func(r *RecurringRule) {
			r.Pattern = NthWeekdayOfMonth
			r.WeekOfMonth = &last
		}, false, true},
		{"fourth is not last", "2025-03-24", func(r *RecurringRule) {
			r.Pattern = NthWeekdayOfMonth
			r.WeekOfMonth = &last
		}, false, false},
		{"second monday", "2025-02-10", func(r *RecurringRule) {
			v := 2
			r.Pattern = NthWeekdayOfMonth
			r.WeekOfMonth = &v
		}, false, true},
		{"invalid occurrence", "2025-02-10", func(r *RecurringRule) {
			v := 6
			r.Pattern = NthWeekdayOfMonth
			r.WeekOfMonth = &v
		}, false, false},
		{"truncated negative week", "2025-01-06", func(r *RecurringRule) {
			v := date("2025-01-07")
			r.ReferenceDate = &v
		}, false, true},
		{"negative whole week", "2025-01-06", func(r *RecurringRule) {
			v := date("2025-01-13")
			r.ReferenceDate = &v
		}, false, false},
	} {
		t.Run(tc.name, func(t *testing.T) {
			r := base
			if tc.change != nil {
				tc.change(&r)
			}
			if got := r.AppliesToDateWithExemptions(date(tc.date), tc.exempt); got != tc.want {
				t.Fatal(got, tc.want)
			}
		})
	}
}
