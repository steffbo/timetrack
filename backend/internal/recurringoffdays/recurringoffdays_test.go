package recurringoffdays_test

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"strings"
	"testing"
	"time"

	openapi_types "github.com/oapi-codegen/runtime/types"
	"github.com/steffbo/timetrack/backend/internal/api"
	"github.com/steffbo/timetrack/backend/internal/clock"
	"github.com/steffbo/timetrack/backend/internal/domain"
	"github.com/steffbo/timetrack/backend/internal/httpx"
	"github.com/steffbo/timetrack/backend/internal/recurringoffdays"
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

// Each test starts with a clean database, test user and secondary user.
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

// insert is RepositoryTestBase.createRecurringOffDay (EVERY_NTH_WEEK, active).
func (e *env) insert(user int64, weekday, interval int, reference, start time.Time, description string) int64 {
	e.t.Helper()
	var id int64
	err := h.Pool.QueryRow(context.Background(), `INSERT INTO recurring_off_days (user_id,recurrence_pattern,weekday,
 week_interval,reference_date,start_date,is_active,description) VALUES($1,'EVERY_NTH_WEEK',$2,$3,$4,$5,true,$6)
 RETURNING id`, user, weekday, interval, reference, start, description).Scan(&id)
	if err != nil {
		e.t.Fatal(err)
	}
	return id
}

// insertDefault is RepositoryTestBase.createRecurringOffDay(user): every 4th Monday.
func (e *env) insertDefault(user int64) int64 {
	return e.insert(user, 1, 4, date(2025, 1, 6), date(2025, 1, 1), "Test recurring off-day")
}

func TestShouldCreateRecurringOffDayWithEveryNthWeekPattern(t *testing.T) {
	e := setup(t)
	status, r, b := e.do("POST", "/api/recurring-off-days", e.testTok, map[string]any{
		"recurrencePattern": "EVERY_NTH_WEEK", "weekday": 1, "weekInterval": 4, "referenceDate": "2025-01-06",
		"startDate": "2025-01-01", "description": "Every 4th Monday off"})
	if status != 201 || r["id"] == nil || r["userId"] != float64(e.testUser) ||
		r["recurrencePattern"] != "EVERY_NTH_WEEK" || r["weekday"] != 1.0 || r["weekInterval"] != 4.0 ||
		r["referenceDate"] != "2025-01-06" || r["isActive"] != true || r["description"] != "Every 4th Monday off" {
		t.Fatal(status, string(b))
	}
}

func TestShouldCreateRecurringOffDayWithNthWeekdayOfMonthPattern(t *testing.T) {
	e := setup(t)
	status, r, b := e.do("POST", "/api/recurring-off-days", e.testTok, map[string]any{
		"recurrencePattern": "NTH_WEEKDAY_OF_MONTH", "weekday": 1, "weekOfMonth": 4, "startDate": "2025-01-01",
		"description": "4th Monday of every month"})
	if status != 201 || r["recurrencePattern"] != "NTH_WEEKDAY_OF_MONTH" || r["weekOfMonth"] != 4.0 ||
		r["weekInterval"] != nil || r["referenceDate"] != nil {
		t.Fatal(status, string(b))
	}
	for _, field := range []string{"weekInterval", "referenceDate", "endDate"} {
		if v, present := r[field]; !present || v != nil {
			t.Fatal("null field missing", field, string(b))
		}
	}
}

func TestShouldFailToCreateWithInvalidWeekday(t *testing.T) {
	e := setup(t)
	// This test calls the service directly, bypassing HTTP validation.
	one, date1, date2 := 4, openapi_types.Date{Time: date(2025, 1, 6)}, openapi_types.Date{Time: date(2025, 1, 1)}
	_, err := recurringoffdays.Service{Pool: h.Pool, Clock: fixedClock}.Create(context.Background(), e.testUser,
		api.CreateRecurringOffDayRequest{RecurrencePattern: api.CreateRecurringOffDayRequestRecurrencePatternEVERYNTHWEEK,
			Weekday: 8, WeekInterval: &one, ReferenceDate: &date1, StartDate: date2})
	var he *httpx.Error
	if !errors.As(err, &he) || he.Status != 400 || he.Code != "INVALID_ARGUMENT" ||
		!strings.Contains(he.Message, "Wochentag muss zwischen 1") {
		t.Fatal(err)
	}
	// Over HTTP the OpenAPI bean validation answers first.
	status, _, b := e.do("POST", "/api/recurring-off-days", e.testTok, map[string]any{
		"recurrencePattern": "EVERY_NTH_WEEK", "weekday": 8, "weekInterval": 4, "referenceDate": "2025-01-06",
		"startDate": "2025-01-01"})
	e.expectError(status, b, 400, "VALIDATION_ERROR", "Validierungsfehler")
}

func TestShouldFailToCreateEveryNthWeekWithoutWeekInterval(t *testing.T) {
	e := setup(t)
	status, _, b := e.do("POST", "/api/recurring-off-days", e.testTok, map[string]any{
		"recurrencePattern": "EVERY_NTH_WEEK", "weekday": 1, "referenceDate": "2025-01-06",
		"startDate": "2025-01-01"})
	e.expectError(status, b, 400, "INVALID_ARGUMENT", "Wochenintervall ist erforderlich")
}

func TestShouldFailToCreateNthWeekdayOfMonthWithoutWeekOfMonth(t *testing.T) {
	e := setup(t)
	status, _, b := e.do("POST", "/api/recurring-off-days", e.testTok, map[string]any{
		"recurrencePattern": "NTH_WEEKDAY_OF_MONTH", "weekday": 1, "startDate": "2025-01-01"})
	e.expectError(status, b, 400, "INVALID_ARGUMENT", "Woche des Monats muss zwischen 1 und 5 liegen")
}

func TestShouldGetAllRecurringOffDaysForUser(t *testing.T) {
	e := setup(t)
	e.insert(e.testUser, 1, 4, date(2025, 1, 6), date(2025, 1, 1), "Description 1")
	e.insert(e.testUser, 5, 4, date(2025, 1, 10), date(2025, 1, 1), "Description 2")
	e.insert(e.other, 1, 4, date(2025, 1, 6), date(2025, 1, 1), "Other user's off-day")
	status, b := testutil.Request(t, e.url, "GET", "/api/recurring-off-days", e.testTok, nil)
	var list []map[string]any
	if err := json.Unmarshal(b, &list); err != nil || status != 200 || len(list) != 2 {
		t.Fatal(status, string(b))
	}
	for _, r := range list {
		if r["userId"] != float64(e.testUser) {
			t.Fatal(string(b))
		}
	}
}

func TestShouldUpdateRecurringOffDay(t *testing.T) {
	e := setup(t)
	id := e.insertDefault(e.testUser)
	status, r, b := e.do("PUT", fmt.Sprintf("/api/recurring-off-days/%d", id), e.testTok,
		map[string]any{"description": "Updated description", "isActive": false})
	if status != 200 || r["description"] != "Updated description" || r["isActive"] != false ||
		r["weekInterval"] != 4.0 {
		t.Fatal(status, string(b))
	}
}

func TestShouldFailToUpdateOtherUsersRecurringOffDay(t *testing.T) {
	e := setup(t)
	id := e.insertDefault(e.other)
	status, _, b := e.do("PUT", fmt.Sprintf("/api/recurring-off-days/%d", id), e.testTok,
		map[string]any{"description": "Updated"})
	e.expectError(status, b, 403, "FORBIDDEN", "keine Berechtigung")
}

func TestShouldDeleteRecurringOffDay(t *testing.T) {
	e := setup(t)
	id := e.insertDefault(e.testUser)
	status, _, b := e.do("DELETE", fmt.Sprintf("/api/recurring-off-days/%d", id), e.testTok, nil)
	if status != 204 || len(b) != 0 {
		t.Fatal(status, string(b))
	}
	found, err := recurringoffdays.FindByID(context.Background(), h.Pool, id)
	if err != nil || found != nil {
		t.Fatal(found, err)
	}
}

func TestShouldFailToDeleteOtherUsersRecurringOffDay(t *testing.T) {
	e := setup(t)
	id := e.insertDefault(e.other)
	status, _, b := e.do("DELETE", fmt.Sprintf("/api/recurring-off-days/%d", id), e.testTok, nil)
	e.expectError(status, b, 403, "FORBIDDEN", "keine Berechtigung")
}

func TestShouldFailToDeleteNonExistentRecurringOffDay(t *testing.T) {
	e := setup(t)
	status, _, b := e.do("DELETE", "/api/recurring-off-days/99999", e.testTok, nil)
	e.expectError(status, b, 404, "RECURRING_OFF_DAY_NOT_FOUND", "Recurring off-day not found with ID: 99999")
}
