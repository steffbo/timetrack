package timeentries_test

import (
	"testing"
	"time"

	te "github.com/steffbo/timetrack/backend/internal/timeentries"
)

// Repository tests cover all nine query cases.
func TestShouldSaveAndFindTimeEntryById(t *testing.T) {
	_, r, uid, _ := setup(t)
	e := completed(t, r, uid, day(), 8)
	if e.ID == 0 || e.CreatedAt.IsZero() {
		t.Fatal(e)
	}
	found, err := r.Find(ctx, e.ID)
	check(t, err)
	if found.EntryType != "WORK" {
		t.Fatal(found)
	}
}
func TestShouldFindTimeEntriesByUserId(t *testing.T) {
	_, r, uid, _ := setup(t)
	completed(t, r, uid, day(), 8)
	entries, err := r.List(ctx, uid, nil, nil)
	check(t, err)
	if len(entries) != 1 || entries[0].UserID != uid {
		t.Fatal(entries)
	}
}
func TestShouldFindTimeEntriesByDateRange(t *testing.T) {
	_, r, uid, _ := setup(t)
	yesterday, today := day().AddDate(0, 0, -1), day()
	completed(t, r, uid, yesterday, 8)
	completed(t, r, uid, today, 8)
	entries, err := r.List(ctx, uid, &yesterday, &today)
	check(t, err)
	if len(entries) != 2 {
		t.Fatal(entries)
	}
}
func TestShouldFindActiveTimeEntry(t *testing.T) {
	s, r, uid, _ := setup(t)
	_, err := s.ClockIn(ctx, uid, nil)
	check(t, err)
	completed(t, r, uid, day(), 8)
	found, err := r.Active(ctx, uid)
	check(t, err)
	if !found.Response().IsActive || found.ClockOut != nil {
		t.Fatal(found)
	}
}
func TestShouldDetectOverlappingTimeEntries(t *testing.T) {
	_, r, uid, _ := setup(t)
	in := day().Add(9 * time.Hour)
	out := day().Add(17 * time.Hour)
	_, err := r.Save(ctx, te.Entry{UserID: uid, EntryDate: day(), ClockIn: in, ClockOut: &out,
		EntryType: "WORK"}, now)
	check(t, err)
	for _, tc := range []struct {
		in, out int
		want    bool
	}{{12, 14, true}, {8, 10, true}, {18, 19, false}} {
		v, err := r.HasOverlappingEntries(ctx, uid, day().Add(time.Duration(tc.in)*time.Hour),
			ptr(day().Add(time.Duration(tc.out)*time.Hour)), nil)
		check(t, err)
		if v != tc.want {
			t.Fatal(tc, v)
		}
	}
}
func TestShouldCalculateTotalHoursWorked(t *testing.T) {
	_, r, uid, _ := setup(t)
	start, end := day().AddDate(0, 0, -2), day()
	completed(t, r, uid, start, 8)
	completed(t, r, uid, start.AddDate(0, 0, 1), 4)
	minutes, err := r.TotalMinutes(ctx, uid, start, end)
	check(t, err)
	if minutes != 720 {
		t.Fatal(minutes)
	}
}
func TestShouldCalculateDurationCorrectly(t *testing.T) {
	_, r, uid, _ := setup(t)
	e := completed(t, r, uid, day(), 8)
	if e.Duration() == nil || *e.Duration() != 8*time.Hour || e.HoursWorked() == nil || *e.HoursWorked() != 8 {
		t.Fatal(e)
	}
}
func TestShouldReturnNullDurationForActiveEntry(t *testing.T) {
	s, _, uid, _ := setup(t)
	e, err := s.ClockIn(ctx, uid, nil)
	check(t, err)
	if e.Duration() != nil || e.HoursWorked() != nil {
		t.Fatal(e)
	}
}
func TestShouldCheckEntryTypeHelperMethods(t *testing.T) {
	_, r, uid, _ := setup(t)
	e := completed(t, r, uid, day(), 8)
	if !e.IsWorkEntry() || e.EntryType != "WORK" {
		t.Fatal(e)
	}
}
