package timeoff_test

import (
	"context"
	"encoding/json"
	"fmt"
	"os"
	"strings"
	"testing"
	"time"

	"github.com/steffbo/timetrack/backend/internal/clock"
	"github.com/steffbo/timetrack/backend/internal/domain"
	"github.com/steffbo/timetrack/backend/internal/leavestore"
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

type env struct {
	t               *testing.T
	url             string
	testUser, other int64
	testTok, otherT string
}

func date(y int, m time.Month, d int) time.Time { return time.Date(y, m, d, 0, 0, 0, 0, time.UTC) }

// Each test starts with a clean database, test user and secondary user.
func setup(t *testing.T) *env {
	t.Helper()
	h.Reset(t)
	pw := "password"
	e := &env{t: t}
	e.testUser = h.User(t, "user@test.local", domain.User, &pw)
	e.other = h.User(t, "other@test.local", domain.User, &pw)
	_, srv := h.Server(t, clock.Fixed{Time: time.Date(2030, 1, 15, 12, 0, 0, 0, time.UTC)})
	e.url = srv.URL
	e.testTok = testutil.Login(t, srv.URL, "user@test.local", pw).AccessToken
	e.otherT = testutil.Login(t, srv.URL, "other@test.local", pw).AccessToken
	return e
}

func (e *env) do(method, path, token string, body any) (int, map[string]any, []byte) {
	e.t.Helper()
	status, b := testutil.Request(e.t, e.url, method, path, token, body)
	var m map[string]any
	_ = json.Unmarshal(b, &m)
	return status, m, b
}

func (e *env) expectError(status int, body []byte, want int, code, msg string) {
	e.t.Helper()
	var m map[string]any
	_ = json.Unmarshal(body, &m)
	if status != want || m["code"] != code || !strings.Contains(fmt.Sprint(m["message"]), msg) {
		e.t.Fatalf("got %d %s, want %d %s containing %q", status, body, want, code, msg)
	}
}

// insert is RepositoryTestBase.createTimeOff.
func (e *env) insert(user int64, start, end time.Time, typ domain.TimeOffType, notes string) int64 {
	e.t.Helper()
	var id int64
	err := h.Pool.QueryRow(context.Background(), `INSERT INTO time_off (user_id,start_date,end_date,time_off_type,notes)
 VALUES($1,$2,$3,$4,$5) RETURNING id`, user, start, end, typ, notes).Scan(&id)
	if err != nil {
		e.t.Fatal(err)
	}
	return id
}

func (e *env) defaultWorkingHours(user int64) {
	e.t.Helper()
	for weekday := 1; weekday <= 7; weekday++ {
		hours := 0.0
		if weekday <= 5 {
			hours = 8
		}
		_, err := h.Pool.Exec(context.Background(), `INSERT INTO working_hours (user_id,weekday,hours,is_working_day)
 VALUES($1,$2,$3,$4)`, user, weekday, hours, weekday <= 5)
		if err != nil {
			e.t.Fatal(err)
		}
	}
}

func TestShouldCreateVacationTimeOff(t *testing.T) {
	e := setup(t)
	status, r, b := e.do("POST", "/api/time-off", e.testTok, map[string]any{"startDate": "2025-07-01",
		"endDate": "2025-07-15", "timeOffType": "VACATION", "notes": "Summer vacation"})
	if status != 201 || r["id"] == nil || r["userId"] != float64(e.testUser) || r["startDate"] != "2025-07-01" ||
		r["endDate"] != "2025-07-15" || r["timeOffType"] != "VACATION" || r["notes"] != "Summer vacation" ||
		r["createdAt"] == nil || r["confirmed"] != false || r["hoursPerDay"] != nil {
		t.Fatal(status, string(b))
	}
}

func TestShouldCreateSickDayTimeOff(t *testing.T) {
	e := setup(t)
	status, r, b := e.do("POST", "/api/time-off", e.testTok, map[string]any{"startDate": "2025-03-10",
		"endDate": "2025-03-12", "timeOffType": "SICK", "notes": "Flu"})
	if status != 201 || r["timeOffType"] != "SICK" || r["notes"] != "Flu" {
		t.Fatal(status, string(b))
	}
}

func TestShouldCreateChildSickTimeOff(t *testing.T) {
	e := setup(t)
	status, r, b := e.do("POST", "/api/time-off", e.testTok, map[string]any{"startDate": "2025-04-15",
		"endDate": "2025-04-17", "timeOffType": "CHILD_SICK", "notes": "Child has flu"})
	if status != 201 || r["timeOffType"] != "CHILD_SICK" || r["notes"] != "Child has flu" ||
		r["userId"] != float64(e.testUser) {
		t.Fatal(status, string(b))
	}
}

func TestShouldCreateTimeOffWithCustomHoursPerDay(t *testing.T) {
	e := setup(t)
	status, r, b := e.do("POST", "/api/time-off", e.testTok, map[string]any{"startDate": "2025-04-01",
		"endDate": "2025-04-01", "timeOffType": "PERSONAL", "hoursPerDay": 4.0, "notes": "Doctor appointment"})
	if status != 201 || r["hoursPerDay"] != 4.0 {
		t.Fatal(status, string(b))
	}
}

func TestShouldFailToCreateWithEndDateBeforeStartDate(t *testing.T) {
	e := setup(t)
	status, _, b := e.do("POST", "/api/time-off", e.testTok, map[string]any{"startDate": "2025-07-15",
		"endDate": "2025-07-01", "timeOffType": "VACATION"})
	e.expectError(status, b, 400, "INVALID_ARGUMENT", "Enddatum muss nach dem Startdatum liegen")
}

func TestShouldFailToCreateWithNegativeHoursPerDay(t *testing.T) {
	e := setup(t)
	status, _, b := e.do("POST", "/api/time-off", e.testTok, map[string]any{"startDate": "2025-07-01",
		"endDate": "2025-07-01", "timeOffType": "VACATION", "hoursPerDay": -1.0})
	e.expectError(status, b, 400, "INVALID_ARGUMENT", "dürfen nicht negativ sein")
}

func TestShouldGetAllTimeOffEntriesForUser(t *testing.T) {
	e := setup(t)
	e.insert(e.testUser, date(2025, 3, 1), date(2025, 3, 5), domain.Vacation, "Test")
	e.insert(e.testUser, date(2025, 7, 1), date(2025, 7, 15), domain.Vacation, "Test")
	e.insert(e.other, date(2025, 3, 1), date(2025, 3, 5), domain.Sick, "Test")
	status, b := testutil.Request(t, e.url, "GET", "/api/time-off", e.testTok, nil)
	var list []map[string]any
	if err := json.Unmarshal(b, &list); err != nil || status != 200 || len(list) != 2 {
		t.Fatal(status, string(b))
	}
	for _, r := range list {
		if r["userId"] != float64(e.testUser) {
			t.Fatal(string(b))
		}
	}
	if list[0]["startDate"] != "2025-07-01" { // ordered by start date descending
		t.Fatal(string(b))
	}
}

func TestShouldGetTimeOffEntriesFilteredByDateRange(t *testing.T) {
	e := setup(t)
	e.insert(e.testUser, date(2025, 3, 1), date(2025, 3, 5), domain.Vacation, "Test time-off")
	e.insert(e.testUser, date(2025, 7, 1), date(2025, 7, 15), domain.Vacation, "Test time-off")
	e.insert(e.testUser, date(2025, 12, 20), date(2025, 12, 31), domain.Vacation, "Test time-off")
	status, b := testutil.Request(t, e.url, "GET", "/api/time-off?startDate=2025-07-01&endDate=2025-08-31",
		e.testTok, nil)
	var list []map[string]any
	if err := json.Unmarshal(b, &list); err != nil || status != 200 || len(list) != 1 ||
		list[0]["startDate"] != "2025-07-01" {
		t.Fatal(status, string(b))
	}
}

func TestShouldUpdateTimeOffEntry(t *testing.T) {
	e := setup(t)
	id := e.insert(e.testUser, date(2025, 7, 1), date(2025, 7, 15), domain.Vacation, "Test time-off")
	status, r, b := e.do("PUT", fmt.Sprintf("/api/time-off/%d", id), e.testTok,
		map[string]any{"notes": "Updated vacation notes", "timeOffType": "PERSONAL"})
	if status != 200 || r["notes"] != "Updated vacation notes" || r["timeOffType"] != "PERSONAL" ||
		r["startDate"] != "2025-07-01" {
		t.Fatal(status, string(b))
	}
}

func TestShouldFailToUpdateOtherUsersTimeOff(t *testing.T) {
	e := setup(t)
	id := e.insert(e.other, date(2025, 7, 1), date(2025, 7, 15), domain.Vacation, "Test time-off")
	status, _, b := e.do("PUT", fmt.Sprintf("/api/time-off/%d", id), e.testTok, map[string]any{"notes": "Updated"})
	e.expectError(status, b, 403, "FORBIDDEN", "keine Berechtigung")
}

func TestShouldDeleteTimeOffEntry(t *testing.T) {
	e := setup(t)
	id := e.insert(e.testUser, date(2025, 7, 1), date(2025, 7, 15), domain.Vacation, "Test time-off")
	status, _, b := e.do("DELETE", fmt.Sprintf("/api/time-off/%d", id), e.testTok, nil)
	if status != 204 || len(b) != 0 {
		t.Fatal(status, string(b))
	}
	found, err := leavestore.FindByID(context.Background(), h.Pool, id)
	if err != nil || found != nil {
		t.Fatal(found, err)
	}
}

func TestShouldFailToDeleteOtherUsersTimeOff(t *testing.T) {
	e := setup(t)
	id := e.insert(e.other, date(2025, 7, 1), date(2025, 7, 15), domain.Vacation, "Test time-off")
	status, _, b := e.do("DELETE", fmt.Sprintf("/api/time-off/%d", id), e.testTok, nil)
	e.expectError(status, b, 403, "FORBIDDEN", "keine Berechtigung")
}

func TestShouldFailToDeleteNonExistentTimeOff(t *testing.T) {
	e := setup(t)
	status, _, b := e.do("DELETE", "/api/time-off/99999", e.testTok, nil)
	e.expectError(status, b, 404, "TIME_OFF_NOT_FOUND", "Time-off entry not found with ID: 99999")
}
