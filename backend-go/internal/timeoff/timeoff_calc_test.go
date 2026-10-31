package timeoff_test

import (
	"context"
	"fmt"
	"testing"

	"github.com/steffbo/timetrack/backend-go/internal/domain"
	"github.com/steffbo/timetrack/backend-go/internal/leavestore"
)

// Port of DomainRepositoryIntegrationTest.shouldFindTimeOffByOrderRangeAndTypeYear.
func TestShouldFindTimeOffByOrderRangeAndTypeYear(t *testing.T) {
	e := setup(t)
	ctx := context.Background()
	vacation := e.insert(e.testUser, date(2026, 1, 10), date(2026, 1, 12), domain.Vacation, "Test time-off")
	sick := e.insert(e.testUser, date(2026, 2, 1), date(2026, 2, 2), domain.Sick, "Sick")
	ordered, err := leavestore.FindByUserIDOrderByStartDateDesc(ctx, h.Pool, e.testUser)
	if err != nil || len(ordered) != 2 || ordered[0].ID != sick || ordered[1].ID != vacation {
		t.Fatal(ordered, err)
	}
	ranged, err := leavestore.FindByUserIDAndDateRange(
		ctx, h.Pool, e.testUser, date(2026, 1, 11), date(2026, 1, 20))
	if err != nil || len(ranged) != 1 || ranged[0].ID != vacation {
		t.Fatal(ranged, err)
	}
	typed, err := leavestore.FindByUserIDAndTypeAndYear(ctx, h.Pool, e.testUser, domain.Vacation,
		date(2026, 1, 1), date(2026, 12, 31))
	if err != nil || len(typed) != 1 || typed[0].ID != vacation {
		t.Fatal(typed, err)
	}
}

func (e *env) days(id int64) float64 {
	e.t.Helper()
	_, r, b := e.do("PUT", fmt.Sprintf("/api/time-off/%d", id), e.testTok, map[string]any{})
	days, ok := r["days"].(float64)
	if !ok {
		e.t.Fatal(string(b))
	}
	return days
}

func TestDaysExcludeWeekendsHolidaysAndRecurringOffDays(t *testing.T) {
	e := setup(t)
	e.defaultWorkingHours(e.testUser)
	_, r, b := e.do("POST", "/api/time-off", e.testTok, map[string]any{"startDate": "2025-07-01",
		"endDate": "2025-07-15", "timeOffType": "VACATION"})
	if r["days"] != 11.0 {
		t.Fatal(string(b))
	}
	// Easter 2025: Good Friday 18 April and Easter Monday 21 April are public holidays.
	_, r, b = e.do("POST", "/api/time-off", e.testTok, map[string]any{"startDate": "2025-04-14",
		"endDate": "2025-04-25", "timeOffType": "VACATION"})
	if r["days"] != 8.0 {
		t.Fatal(string(b))
	}
	// A recurring off-day every Monday reduces vacation days but not sick days.
	_, err := h.Pool.Exec(context.Background(), `INSERT INTO recurring_off_days (user_id,recurrence_pattern,weekday,
 week_interval,reference_date,start_date,is_active) VALUES($1,'EVERY_NTH_WEEK',1,1,'2025-01-06','2025-01-01',true)`,
		e.testUser)
	if err != nil {
		t.Fatal(err)
	}
	vacation := e.insert(e.testUser, date(2025, 9, 1), date(2025, 9, 5), domain.Vacation, "v")
	sick := e.insert(e.testUser, date(2025, 10, 6), date(2025, 10, 10), domain.Sick, "s")
	if got := e.days(vacation); got != 4 {
		t.Fatal(got)
	}
	if got := e.days(sick); got != 5 {
		t.Fatal(got)
	}
}

func TestHalfDayHolidaysCountHalf(t *testing.T) {
	e := setup(t)
	e.defaultWorkingHours(e.testUser)
	_, err := h.Pool.Exec(context.Background(), `UPDATE users SET half_day_holidays_enabled=true WHERE id=$1`, e.testUser)
	if err != nil {
		t.Fatal(err)
	}
	// Mon 22 .. Wed 31 December 2025: 22,23,24(half),29,30,31(half); 25 and 26 are public holidays.
	_, r, b := e.do("POST", "/api/time-off", e.testTok, map[string]any{"startDate": "2025-12-22",
		"endDate": "2025-12-31", "timeOffType": "PERSONAL"})
	if r["days"] != 5.0 {
		t.Fatal(string(b))
	}
}

func TestVacationBalanceIsRecalculated(t *testing.T) {
	e := setup(t)
	e.defaultWorkingHours(e.testUser)
	balance := func(year int) (used, remaining float64, found bool) {
		err := h.Pool.QueryRow(context.Background(), `SELECT used_days::float8,remaining_days::float8
 FROM vacation_balance WHERE user_id=$1 AND year=$2`, e.testUser, year).Scan(&used, &remaining)
		return used, remaining, err == nil
	}
	_, r, b := e.do("POST", "/api/time-off", e.testTok, map[string]any{"startDate": "2025-07-01",
		"endDate": "2025-07-15", "timeOffType": "VACATION"})
	if used, remaining, ok := balance(2025); !ok || used != 11 || remaining != 19 {
		t.Fatal(used, remaining, ok, string(b))
	}
	id := int64(r["id"].(float64))
	// Switching the type away from vacation recalculates (the clock is in 2030, so everything is past).
	e.do("PUT", fmt.Sprintf("/api/time-off/%d", id), e.testTok, map[string]any{"timeOffType": "SICK"})
	if used, remaining, ok := balance(2025); !ok || used != 0 || remaining != 30 {
		t.Fatal(used, remaining, ok)
	}
	e.do("PUT", fmt.Sprintf("/api/time-off/%d", id), e.testTok, map[string]any{"timeOffType": "VACATION"})
	if used, _, _ := balance(2025); used != 11 {
		t.Fatal(used)
	}
	if status, _, b := e.do("DELETE", fmt.Sprintf("/api/time-off/%d", id), e.testTok, nil); status != 204 {
		t.Fatal(string(b))
	}
	if used, remaining, _ := balance(2025); used != 0 || remaining != 30 {
		t.Fatal(used, remaining)
	}
	// Future vacation is planned, not used.
	e.do("POST", "/api/time-off", e.testTok, map[string]any{"startDate": "2031-07-01", "endDate": "2031-07-02",
		"timeOffType": "VACATION"})
	if used, remaining, ok := balance(2031); !ok || used != 0 || remaining != 30 {
		t.Fatal(used, remaining, ok)
	}
}

// Both CRUD and GET must use the same calculator, preserving Java's multi-year
// overlap semantics (the entire absence contributes to each overlapping year).
func TestSharedBalanceRecalculationAndStableRead(t *testing.T) {
	e := setup(t)
	e.defaultWorkingHours(e.testUser)
	_, entry, b := e.do("POST", "/api/time-off", e.testTok, map[string]any{
		"startDate": "2025-12-30", "endDate": "2026-01-02", "timeOffType": "VACATION"})
	if entry["days"] != 3.0 {
		t.Fatal(string(b))
	}
	ctx := context.Background()
	for _, year := range []int{2025, 2026} {
		var used float64
		err := h.Pool.QueryRow(ctx, `SELECT used_days::float8 FROM vacation_balance
   WHERE user_id=$1 AND year=$2`, e.testUser, year).Scan(&used)
		if err != nil || used != 3 {
			t.Fatal(used, err)
		}
		// Give the existing, unchanged row an observable timestamp older than the clock.
		_, err = h.Pool.Exec(ctx, `UPDATE vacation_balance SET updated_at='2029-01-01'
   WHERE user_id=$1 AND year=$2`, e.testUser, year)
		if err != nil {
			t.Fatal(err)
		}
		status, balance, b := e.do("GET", fmt.Sprintf("/api/vacation-balance?year=%d", year), e.testTok, nil)
		if status != 200 || balance["usedDays"] != 3.0 || balance["plannedDays"] != 3.0 ||
			balance["remainingDays"] != 27.0 || balance["updatedAt"] != "2029-01-01T00:00:00Z" {
			t.Fatal(string(b))
		}

		status, balance, b = e.do("PUT", "/api/vacation-balance", e.testTok,
			map[string]any{"userId": e.testUser, "year": year})
		if status != 200 || balance["updatedAt"] != "2029-01-01T00:00:00Z" {
			t.Fatal(string(b))
		}
	}
	// A non-vacation mutation does not recalculate storage in Java; GET does.
	e.do("POST", "/api/time-off", e.testTok, map[string]any{
		"startDate": "2025-12-30", "endDate": "2025-12-30", "timeOffType": "SICK"})
	var stored float64
	err := h.Pool.QueryRow(ctx, `SELECT used_days::float8 FROM vacation_balance
  WHERE user_id=$1 AND year=2025`, e.testUser).Scan(&stored)
	if err != nil || stored != 3 {
		t.Fatal(stored, err)
	}
	_, balance, b := e.do("GET", "/api/vacation-balance?year=2025", e.testTok, nil)
	if balance["usedDays"] != 2.0 || balance["plannedDays"] != 2.0 {
		t.Fatal(string(b))
	}
}
