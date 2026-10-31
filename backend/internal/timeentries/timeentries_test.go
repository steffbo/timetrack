package timeentries_test

import (
	"context"
	"errors"
	"fmt"
	"os"
	"strings"
	"testing"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/steffbo/timetrack/backend-go/internal/api"
	"github.com/steffbo/timetrack/backend-go/internal/clock"
	"github.com/steffbo/timetrack/backend-go/internal/domain"
	"github.com/steffbo/timetrack/backend-go/internal/testutil"
	te "github.com/steffbo/timetrack/backend-go/internal/timeentries"
)

var h *testutil.Harness
var ctx = context.Background()

// Monday: deterministic Java LocalDate.now replacement.
var now = time.Date(2030, 1, 14, 18, 0, 0, 0, time.UTC)

func TestMain(m *testing.M) {
	var err error
	h, err = testutil.Start(ctx)
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
	code := m.Run()
	h.Close(ctx)
	os.Exit(code)
}
func check(t *testing.T, err error) {
	t.Helper()
	if err != nil {
		t.Fatal(err)
	}
}
func ptr[T any](v T) *T { return &v }
func setup(t *testing.T) (te.Service, te.Repository, int64, int64) {
	t.Helper()
	h.Reset(t)
	uid := h.User(t, "test@example.com", domain.User, nil)
	other := h.User(t, "other@example.com", domain.User, nil)
	for _, id := range []int64{uid, other} {
		_, err := h.Pool.Exec(ctx, `INSERT INTO working_hours(user_id,weekday,hours,is_working_day)
   SELECT $1,n,CASE WHEN n<6 THEN 8 ELSE 0 END,n<6 FROM generate_series(1,7)n`, id)
		check(t, err)
	}
	return te.Service{Pool: h.Pool, Clock: clock.Fixed{Time: now}}, te.Repository{DB: h.Pool}, uid, other
}
func completed(t *testing.T, r te.Repository, uid int64, date time.Time, hours float64) te.Entry {
	t.Helper()
	in := time.Date(date.Year(), date.Month(), date.Day(), 8, 0, 0, 0, time.UTC)
	out := in.Add(time.Duration(hours * float64(time.Hour)))
	e, err := r.Save(ctx, te.Entry{UserID: uid, EntryDate: in, ClockIn: in, ClockOut: &out,
		EntryType: "WORK"}, now)
	check(t, err)
	return e
}
func requireError(t *testing.T, err error, part string) {
	t.Helper()
	if err == nil || !strings.Contains(err.Error(), part) {
		t.Fatalf("expected %q, got %v", part, err)
	}
}
func list(t *testing.T, s te.Service, uid int64, start, end *time.Time) []te.Entry {
	t.Helper()
	v, err := s.List(ctx, uid, start, end)
	check(t, err)
	return v
}
func summary(t *testing.T, s te.Service, uid int64, start, end time.Time) []api.DailySummaryResponse {
	t.Helper()
	v, err := s.Summary(ctx, uid, start, end)
	check(t, err)
	return v
}
func day() time.Time { return time.Date(now.Year(), now.Month(), now.Day(), 0, 0, 0, 0, time.UTC) }

// TimeEntryIntegrationTest: one named Go test per Java @Test, same domain assertions.
func TestShouldClockInSuccessfully(t *testing.T) {
	s, _, uid, _ := setup(t)
	e, err := s.ClockIn(ctx, uid, ptr("Starting work"))
	check(t, err)
	if e.ID == 0 || e.UserID != uid || e.ClockIn.IsZero() || e.ClockOut != nil || !e.EntryDate.Equal(day()) ||
		e.EntryType != "WORK" || e.Notes == nil || *e.Notes != "Starting work" || !e.Response().IsActive {
		t.Fatal(e)
	}
}
func TestShouldFailToClockInWhenAlreadyClockedIn(t *testing.T) {
	s, _, uid, _ := setup(t)
	_, err := s.ClockIn(ctx, uid, nil)
	check(t, err)
	_, err = s.ClockIn(ctx, uid, nil)
	requireError(t, err, "Bereits eingecheckt")
}
func TestShouldAllowDifferentUsersToClockInSimultaneously(t *testing.T) {
	s, _, uid, other := setup(t)
	for _, id := range []int64{uid, other} {
		e, err := s.ClockIn(ctx, id, nil)
		check(t, err)
		if e.UserID != id || e.ClockOut != nil {
			t.Fatal(e)
		}
	}
}
func TestShouldClockOutSuccessfully(t *testing.T) {
	s, _, uid, _ := setup(t)
	in, err := s.ClockIn(ctx, uid, ptr("Morning session"))
	check(t, err)
	s.Clock = clock.Fixed{Time: now.Add(time.Second)}
	e, err := s.ClockOut(ctx, uid, ptr("End of day"))
	check(t, err)
	if e.ID != in.ID || e.ClockOut == nil || !e.ClockOut.After(e.ClockIn) || e.Response().IsActive ||
		e.HoursWorked() == nil || *e.HoursWorked() < 0 || e.Notes == nil || *e.Notes != "End of day" {
		t.Fatal(e)
	}
}
func TestShouldFailToClockOutWhenNotClockedIn(t *testing.T) {
	s, _, uid, _ := setup(t)
	_, err := s.ClockOut(ctx, uid, nil)
	requireError(t, err, "Nicht eingecheckt")
}
func TestShouldUpdateNotesWhenClockingOut(t *testing.T) {
	s, _, uid, _ := setup(t)
	_, err := s.ClockIn(ctx, uid, ptr("Initial notes"))
	check(t, err)
	s.Clock = clock.Fixed{Time: now.Add(time.Second)}
	e, err := s.ClockOut(ctx, uid, ptr("Updated notes"))
	check(t, err)
	if e.Notes == nil || *e.Notes != "Updated notes" {
		t.Fatal(e)
	}
}
func TestShouldGetAllTimeEntriesForUser(t *testing.T) {
	s, r, uid, _ := setup(t)
	for i := 0; i < 3; i++ {
		completed(t, r, uid, day().AddDate(0, 0, -i), 8)
	}
	if v := list(t, s, uid, nil, nil); len(v) != 3 {
		t.Fatal(v)
	}
}
func TestShouldGetTimeEntriesWithinDateRange(t *testing.T) {
	s, r, uid, _ := setup(t)
	start, end := day().AddDate(0, 0, -5), day().AddDate(0, 0, -1)
	dates := []time.Time{start.AddDate(0, 0, -1), start, start.AddDate(0, 0, 2), end, end.AddDate(0, 0, 1)}
	for _, d := range dates {
		completed(t, r, uid, d, 8)
	}
	if v := list(t, s, uid, &start, &end); len(v) != 3 {
		t.Fatal(v)
	}
}
func TestShouldReturnEntriesSortedByClockInDescending(t *testing.T) {
	s, r, uid, _ := setup(t)
	for i := 2; i >= 0; i-- {
		completed(t, r, uid, day().AddDate(0, 0, -i), 8)
	}
	v := list(t, s, uid, nil, nil)
	if len(v) != 3 {
		t.Fatal(v)
	}
	for i, e := range v {
		if !e.EntryDate.Equal(day().AddDate(0, 0, -i)) {
			t.Fatal(v)
		}
	}
}
func TestShouldNotReturnOtherUsersEntries(t *testing.T) {
	s, r, uid, other := setup(t)
	completed(t, r, uid, day(), 8)
	completed(t, r, other, day(), 8)
	v := list(t, s, uid, nil, nil)
	if len(v) != 1 || v[0].UserID != uid {
		t.Fatal(v)
	}
}
func TestShouldCalculateDailySummaryWithMatchedHours(t *testing.T) {
	s, r, uid, _ := setup(t)
	completed(t, r, uid, day(), 8)
	v := summary(t, s, uid, day(), day())
	if len(v) != 1 {
		t.Fatal(v)
	}
	e := v[0]
	if !e.Date.Time.Equal(day()) || e.ActualHours != 8 || e.ExpectedHours <= 0 || e.Status != "MATCHED" ||
		e.Entries == nil || len(*e.Entries) != 1 {
		t.Fatal(e)
	}
}
func TestShouldCalculateDailySummaryWithBelowExpectedHours(t *testing.T) {
	s, r, uid, _ := setup(t)
	completed(t, r, uid, day(), 6)
	e := summary(t, s, uid, day(), day())[0]
	if e.ActualHours != 6 || e.Status != "BELOW_EXPECTED" {
		t.Fatal(e)
	}
}
func TestShouldCalculateDailySummaryWithAboveExpectedHours(t *testing.T) {
	s, r, uid, _ := setup(t)
	completed(t, r, uid, day(), 10)
	e := summary(t, s, uid, day(), day())[0]
	if e.ActualHours != 10 || e.Status != "ABOVE_EXPECTED" {
		t.Fatal(e)
	}
}
func TestShouldShowNoEntryStatusForDaysWithoutEntries(t *testing.T) {
	s, _, uid, _ := setup(t)
	e := summary(t, s, uid, day(), day())[0]
	if e.ActualHours != 0 || e.Status != "NO_ENTRY" || e.Entries == nil || len(*e.Entries) != 0 {
		t.Fatal(e)
	}
}
func TestShouldGenerateSummaryForDateRange(t *testing.T) {
	s, r, uid, _ := setup(t)
	start := day().AddDate(0, 0, -3)
	completed(t, r, uid, start, 8)
	completed(t, r, uid, day(), 8)
	if v := summary(t, s, uid, start, day()); len(v) != 4 {
		t.Fatal(v)
	}
}
func TestShouldAggregateMultipleEntriesPerDay(t *testing.T) {
	s, r, uid, _ := setup(t)
	completed(t, r, uid, day(), 4)
	completed(t, r, uid, day(), 4)
	e := summary(t, s, uid, day(), day())[0]
	if e.ActualHours != 8 || len(*e.Entries) != 2 {
		t.Fatal(e)
	}
}
func updateRequest(e te.Entry) api.UpdateTimeEntryRequest {
	return api.UpdateTimeEntryRequest{ClockIn: e.ClockIn, ClockOut: e.ClockOut, EntryType: "WORK"}
}
func TestShouldUpdateTimeEntrySuccessfully(t *testing.T) {
	s, r, uid, _ := setup(t)
	e := completed(t, r, uid, day(), 8)
	req := api.UpdateTimeEntryRequest{ClockIn: now.Add(-9 * time.Hour), ClockOut: ptr(now.Add(-time.Hour)),
		EntryType: "WORK", BreakMinutes: ptr(0), Notes: ptr("Updated notes")}
	v, err := s.Update(ctx, uid, e.ID, req)
	check(t, err)
	if v.ID != e.ID || !v.Response().ClockIn.Equal(req.ClockIn) || !v.Response().ClockOut.Equal(*req.ClockOut) ||
		v.Notes == nil || *v.Notes != "Updated notes" {
		t.Fatal(v)
	}
}
func TestShouldUpdateBreakMinutesSuccessfully(t *testing.T) {
	s, r, uid, _ := setup(t)
	e := completed(t, r, uid, day(), 8)
	req := updateRequest(e)
	req.BreakMinutes = ptr(30)
	req.Notes = ptr("With break")
	v, err := s.Update(ctx, uid, e.ID, req)
	check(t, err)
	if v.BreakMinutes != 30 || *v.HoursWorked() >= 8 || *v.HoursWorked() != 7.5 {
		t.Fatal(v)
	}
}
func TestShouldFailToUpdateNonExistentEntry(t *testing.T) {
	s, _, uid, _ := setup(t)
	_, err := s.Update(ctx, uid, 99999, api.UpdateTimeEntryRequest{})
	requireError(t, err, "nicht gefunden")
}
func TestShouldFailToUpdateOtherUsersEntry(t *testing.T) {
	s, r, uid, other := setup(t)
	e := completed(t, r, other, day(), 8)
	_, err := s.Update(ctx, uid, e.ID, updateRequest(e))
	requireError(t, err, "keine Berechtigung")
}
func TestShouldFailToUpdateWithClockOutBeforeClockIn(t *testing.T) {
	s, r, uid, _ := setup(t)
	e := completed(t, r, uid, day(), 8)
	req := updateRequest(e)
	req.ClockIn = now
	req.ClockOut = ptr(now.Add(-time.Hour))
	_, err := s.Update(ctx, uid, e.ID, req)
	requireError(t, err, "Checkout-Zeit muss nach der Checkin-Zeit liegen")
}
func TestShouldDeleteTimeEntrySuccessfully(t *testing.T) {
	s, r, uid, _ := setup(t)
	e := completed(t, r, uid, day(), 8)
	check(t, s.Delete(ctx, uid, e.ID))
	_, err := r.Find(ctx, e.ID)
	if !errors.Is(err, pgx.ErrNoRows) {
		t.Fatal(err)
	}
}
func TestShouldFailToDeleteNonExistentEntry(t *testing.T) {
	s, _, uid, _ := setup(t)
	requireError(t, s.Delete(ctx, uid, 99999), "nicht gefunden")
}
func TestShouldFailToDeleteOtherUsersEntry(t *testing.T) {
	s, r, uid, other := setup(t)
	e := completed(t, r, other, day(), 8)
	requireError(t, s.Delete(ctx, uid, e.ID), "keine Berechtigung")
}
