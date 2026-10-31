package conflictwarnings_test

import (
	"context"
	"encoding/json"
	"fmt"
	"os"
	"strings"
	"testing"
	"time"

	"github.com/steffbo/timetrack/backend/internal/clock"
	"github.com/steffbo/timetrack/backend/internal/conflictwarnings"
	"github.com/steffbo/timetrack/backend/internal/domain"
	"github.com/steffbo/timetrack/backend/internal/testutil"
)

var h *testutil.Harness

func TestMain(m *testing.M) {
	ctx := context.Background()
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

var fixedClock = clock.Fixed{Time: time.Date(2030, 1, 15, 12, 0, 0, 0, time.UTC)}

type env struct {
	t               *testing.T
	url             string
	testUser, other int64
	testTok, otherT string
}

func date(y int, m time.Month, d int) time.Time { return time.Date(y, m, d, 0, 0, 0, 0, time.UTC) }

func setup(t *testing.T) *env {
	t.Helper()
	h.Reset(t)
	pw := "password"
	e := &env{t: t}
	e.testUser = h.User(t, "user@test.local", domain.User, &pw)
	e.other = h.User(t, "other@test.local", domain.User, &pw)
	_, srv := h.Server(t, fixedClock)
	e.url = srv.URL
	e.testTok = testutil.Login(t, srv.URL, "user@test.local", pw).AccessToken
	e.otherT = testutil.Login(t, srv.URL, "other@test.local", pw).AccessToken
	return e
}

func (e *env) exec(sql string, args ...any) {
	e.t.Helper()
	if _, err := h.Pool.Exec(context.Background(), sql, args...); err != nil {
		e.t.Fatal(err)
	}
}

func (e *env) id(sql string, args ...any) int64 {
	e.t.Helper()
	var id int64
	if err := h.Pool.QueryRow(context.Background(), sql, args...).Scan(&id); err != nil {
		e.t.Fatal(err)
	}
	return id
}

func (e *env) rule(user int64, description string) int64 {
	return e.id(`INSERT INTO recurring_off_days (user_id,recurrence_pattern,weekday,week_interval,reference_date,
 start_date,is_active,description) VALUES($1,'EVERY_NTH_WEEK',1,2,'2026-01-05','2026-01-01',true,$2) RETURNING id`,
		user, description)
}

func (e *env) entry(user int64, day time.Time) int64 {
	return e.id(`INSERT INTO time_entries (user_id,entry_date,clock_in,clock_out,entry_type)
 VALUES($1,$2,$3,$4,'WORK') RETURNING id`, user, day, day.Add(9*time.Hour), day.Add(17*time.Hour))
}

func (e *env) do(method, path, token string) (int, []byte) {
	e.t.Helper()
	return testutil.Request(e.t, e.url, method, path, token, nil)
}

// Conflict warning repository supports lookup, acknowledgment and deletion.
func TestShouldFindAcknowledgeAndDeleteConflictWarnings(t *testing.T) {
	e := setup(t)
	ctx := context.Background()
	rule := e.rule(e.testUser, "")
	oldEntry := e.entry(e.testUser, date(2026, 1, 5))
	newEntry := e.entry(e.testUser, date(2026, 1, 19))
	oldWarning := conflictwarnings.Warning{UserID: e.testUser, ConflictDate: date(2026, 1, 5),
		TimeEntryID: &oldEntry, RecurringOffDayID: &rule}
	newWarning := conflictwarnings.Warning{UserID: e.testUser, ConflictDate: date(2026, 1, 19),
		TimeEntryID: &newEntry, RecurringOffDayID: &rule}
	for _, w := range []*conflictwarnings.Warning{&oldWarning, &newWarning} {
		if err := conflictwarnings.Insert(ctx, h.Pool, w, time.Now()); err != nil {
			t.Fatal(err)
		}
	}
	if err := conflictwarnings.Acknowledge(ctx, h.Pool, &oldWarning, time.Date(2026, 1, 6, 10, 0, 0, 0, time.UTC)); err != nil {
		t.Fatal(err)
	}
	all, err := conflictwarnings.FindByUserIDOrderByConflictDateDesc(ctx, h.Pool, e.testUser)
	if err != nil || len(all) != 2 || all[0].ID != newWarning.ID || all[1].ID != oldWarning.ID {
		t.Fatal(all, err)
	}
	open, err := conflictwarnings.FindUnacknowledgedByUserID(ctx, h.Pool, e.testUser)
	if err != nil || len(open) != 1 || open[0].ID != newWarning.ID {
		t.Fatal(open, err)
	}
	ranged, err := conflictwarnings.FindByUserIDAndDateRange(ctx, h.Pool, e.testUser, date(2026, 1, 1), date(2026, 1, 10))
	if err != nil || len(ranged) != 1 || ranged[0].ID != oldWarning.ID {
		t.Fatal(ranged, err)
	}
	byDate, err := conflictwarnings.FindByUserIDAndConflictDate(ctx, h.Pool, e.testUser, date(2026, 1, 19))
	if err != nil || byDate == nil || byDate.ID != newWarning.ID {
		t.Fatal(byDate, err)
	}
	if ok, err := conflictwarnings.ExistsByUserIDAndConflictDate(ctx, h.Pool, e.testUser, date(2026, 1, 19)); err != nil || !ok {
		t.Fatal(ok, err)
	}
	if err = conflictwarnings.DeleteByTimeEntryID(ctx, h.Pool, newEntry); err != nil {
		t.Fatal(err)
	}
	gone, err := conflictwarnings.FindByID(ctx, h.Pool, newWarning.ID)
	if err != nil || gone != nil {
		t.Fatal(gone, err)
	}
}

func TestConflictWarningEndpoints(t *testing.T) {
	e := setup(t)
	rule := e.rule(e.testUser, "Every other Monday")
	oldEntry := e.entry(e.testUser, date(2026, 1, 5))
	newEntry := e.entry(e.testUser, date(2026, 1, 19))
	otherRule := e.rule(e.other, "x")
	otherEntry := e.entry(e.other, date(2026, 1, 5))
	insert := func(user int64, day time.Time, entry, rule int64) int64 {
		return e.id(`INSERT INTO recurring_off_day_conflict_warnings (user_id,conflict_date,time_entry_id,
 recurring_off_day_id) VALUES($1,$2,$3,$4) RETURNING id`, user, day, entry, rule)
	}
	first := insert(e.testUser, date(2026, 1, 5), oldEntry, rule)
	second := insert(e.testUser, date(2026, 1, 19), newEntry, rule)
	foreign := insert(e.other, date(2026, 1, 5), otherEntry, otherRule)

	status, b := e.do("GET", "/api/recurring-off-day-warnings", e.testTok)
	var list []map[string]any
	if err := json.Unmarshal(b, &list); err != nil || status != 200 || len(list) != 2 ||
		list[0]["id"] != float64(second) || list[0]["recurringOffDayDescription"] != "Every other Monday" ||
		list[0]["acknowledged"] != false || list[0]["acknowledgedAt"] != nil || list[0]["conflictDate"] != "2026-01-19" ||
		list[0]["timeEntryId"] != float64(newEntry) || list[0]["recurringOffDayId"] != float64(rule) {
		t.Fatal(status, string(b))
	}

	status, b = e.do("POST", fmt.Sprintf("/api/recurring-off-day-warnings/%d/acknowledge", first), e.testTok)
	var ack map[string]any
	if err := json.Unmarshal(b, &ack); err != nil || status != 200 || ack["acknowledged"] != true ||
		ack["acknowledgedAt"] != "2030-01-15T12:00:00Z" {
		t.Fatal(status, string(b))
	}
	status, b = e.do("GET", "/api/recurring-off-day-warnings?unacknowledgedOnly=true", e.testTok)
	if err := json.Unmarshal(b, &list); err != nil || status != 200 || len(list) != 1 || list[0]["id"] != float64(second) {
		t.Fatal(status, string(b))
	}
	status, b = e.do("POST", fmt.Sprintf("/api/recurring-off-day-warnings/%d/acknowledge", foreign), e.testTok)
	expect(t, status, b, 400, "INVALID_ARGUMENT", "Warning does not belong to this user")
	status, b = e.do("POST", "/api/recurring-off-day-warnings/99999/acknowledge", e.testTok)
	expect(t, status, b, 400, "INVALID_ARGUMENT", "Warning not found with ID: 99999")
}

func expect(t *testing.T, status int, body []byte, want int, code, msg string) {
	t.Helper()
	var m map[string]any
	_ = json.Unmarshal(body, &m)
	if status != want || m["code"] != code || !strings.Contains(fmt.Sprint(m["message"]), msg) {
		t.Fatalf("got %d %s, want %d %s %q", status, body, want, code, msg)
	}
}

func TestDetector(t *testing.T) {
	e := setup(t)
	ctx := context.Background()
	d := conflictwarnings.Detector{Clock: fixedClock}
	rule := e.rule(e.testUser, "r")
	conflicting := e.entry(e.testUser, date(2026, 1, 19))
	free := e.entry(e.testUser, date(2026, 1, 12))
	exempt := e.entry(e.testUser, date(2026, 2, 2))
	e.exec(`INSERT INTO recurring_off_day_exemptions (recurring_off_day_id,exemption_date) VALUES($1,'2026-02-02')`, rule)

	w, err := d.DetectAndCreateIfNeeded(ctx, h.Pool, conflictwarnings.TimeEntryRef{ID: conflicting,
		UserID: e.testUser, EntryDate: date(2026, 1, 19)})
	if err != nil || w == nil || w.Acknowledged || *w.TimeEntryID != conflicting || *w.RecurringOffDayID != rule {
		t.Fatal(w, err)
	}
	again, err := d.DetectAndCreateIfNeeded(ctx, h.Pool, conflictwarnings.TimeEntryRef{ID: conflicting,
		UserID: e.testUser, EntryDate: date(2026, 1, 19)})
	if err != nil || again == nil || again.ID != w.ID {
		t.Fatal(again, err)
	}
	for _, c := range []conflictwarnings.TimeEntryRef{{ID: free, UserID: e.testUser, EntryDate: date(2026, 1, 12)},
		{ID: exempt, UserID: e.testUser, EntryDate: date(2026, 2, 2)}, {}} {
		if got, err := d.DetectAndCreateIfNeeded(ctx, h.Pool, c); err != nil || got != nil {
			t.Fatal(c, got, err)
		}
	}
	d.CleanupForTimeEntry(ctx, h.Pool, conflicting)
	if all, _ := conflictwarnings.FindByUserIDOrderByConflictDateDesc(ctx, h.Pool, e.testUser); len(all) != 0 {
		t.Fatal(all)
	}
	err = d.Reevaluate(ctx, h.Pool, e.testUser, []conflictwarnings.TimeEntryRef{
		{ID: conflicting, UserID: e.testUser, EntryDate: date(2026, 1, 19)}})
	if all, _ := conflictwarnings.FindByUserIDOrderByConflictDateDesc(ctx, h.Pool, e.testUser); err != nil || len(all) != 1 {
		t.Fatal(all, err)
	}
}
