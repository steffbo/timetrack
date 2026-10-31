package recurringoffdays_test

import (
	"context"
	"encoding/json"
	"fmt"
	"testing"
	"time"

	"github.com/steffbo/timetrack/backend/internal/domain"
	"github.com/steffbo/timetrack/backend/internal/recurringoffdays"
	"github.com/steffbo/timetrack/backend/internal/testutil"
)

// Recurring off-day repository loads rules and exemptions.
func TestShouldFindRecurringOffDaysAndExemptions(t *testing.T) {
	e := setup(t)
	ctx := context.Background()
	id := e.insert(e.testUser, 1, 2, date(2026, 1, 5), date(2026, 1, 1), "")
	var first, second int64
	for _, c := range []struct {
		day time.Time
		out *int64
	}{{date(2026, 1, 5), &first}, {date(2026, 1, 19), &second}} {
		ex := recurringoffdays.Exemption{RecurringOffDayID: id, Date: c.day}
		reason := "Work day"
		ex.Reason = &reason
		if err := recurringoffdays.InsertExemption(ctx, h.Pool, &ex, time.Now()); err != nil {
			t.Fatal(err)
		}
		*c.out = ex.ID
	}
	all, err := recurringoffdays.FindByUserID(ctx, h.Pool, e.testUser)
	if err != nil || len(all) != 1 || all[0].ID != id {
		t.Fatal(all, err)
	}
	active, err := recurringoffdays.FindActiveByUserIDAndDate(ctx, h.Pool, e.testUser, date(2026, 1, 5))
	if err != nil || len(active) != 1 || active[0].ID != id {
		t.Fatal(active, err)
	}
	list, err := recurringoffdays.FindExemptions(ctx, h.Pool, id)
	if err != nil || len(list) != 2 || list[0].ID != second || list[1].ID != first {
		t.Fatal(list, err)
	}
	if ok, err := recurringoffdays.ExemptionExists(ctx, h.Pool, id, date(2026, 1, 5)); err != nil || !ok {
		t.Fatal(ok, err)
	}
	if err = recurringoffdays.DeleteExemptionsByRule(ctx, h.Pool, id); err != nil {
		t.Fatal(err)
	}
	list, err = recurringoffdays.FindExemptions(ctx, h.Pool, id)
	if err != nil || len(list) != 0 {
		t.Fatal(list, err)
	}
}

func TestExemptionEndpoints(t *testing.T) {
	e := setup(t)
	id := e.insert(e.testUser, 1, 2, date(2026, 1, 5), date(2026, 1, 1), "biweekly")
	base := fmt.Sprintf("/api/recurring-off-days/%d/exemptions", id)
	status, r, b := e.do("POST", base, e.testTok, map[string]any{"exemptionDate": "2026-01-19", "reason": "Work day"})
	if status != 201 || r["recurringOffDayId"] != float64(id) || r["exemptionDate"] != "2026-01-19" ||
		r["reason"] != "Work day" || r["createdAt"] == nil {
		t.Fatal(status, string(b))
	}
	exemptionID := int64(r["id"].(float64))
	status, _, b = e.do("POST", base, e.testTok, map[string]any{"exemptionDate": "2026-01-19"})
	e.expectError(status, b, 409, "EXEMPTION_ALREADY_EXISTS", "Exemption already exists for date: 2026-01-19")
	status, _, b = e.do("POST", base, e.testTok, map[string]any{"exemptionDate": "2026-01-12"})
	e.expectError(status, b, 400, "INVALID_ARGUMENT", "Date 2026-01-12 does not match the recurring off-day pattern")
	status, _, b = e.do("POST", base, e.otherT, map[string]any{"exemptionDate": "2026-02-02"})
	e.expectError(status, b, 400, "INVALID_ARGUMENT",
		fmt.Sprintf("Recurring off-day does not belong to user: %d", e.other))
	status, _, b = e.do("POST", "/api/recurring-off-days/99999/exemptions", e.testTok,
		map[string]any{"exemptionDate": "2026-02-02"})
	e.expectError(status, b, 400, "INVALID_ARGUMENT", "Recurring off-day not found with ID: 99999")

	e.do("POST", base, e.testTok, map[string]any{"exemptionDate": "2026-02-02"})
	status, b = testutil.Request(t, e.url, "GET", base, e.testTok, nil)
	var list []map[string]any
	if err := json.Unmarshal(b, &list); err != nil || status != 200 || len(list) != 2 ||
		list[0]["exemptionDate"] != "2026-02-02" || list[0]["reason"] != nil {
		t.Fatal(status, string(b))
	}
	status, _, b = e.do("GET", base, e.otherT, nil)
	e.expectError(status, b, 400, "INVALID_ARGUMENT", "does not belong to user")

	other := e.insertDefault(e.testUser)
	status, _, b = e.do("DELETE", fmt.Sprintf("/api/recurring-off-days/%d/exemptions/%d", other, exemptionID),
		e.testTok, nil)
	e.expectError(status, b, 400, "INVALID_ARGUMENT",
		fmt.Sprintf("Exemption does not belong to recurring off-day: %d", other))
	status, _, b = e.do("DELETE", fmt.Sprintf("/api/recurring-off-days/%d/exemptions/99999", id), e.testTok, nil)
	e.expectError(status, b, 400, "INVALID_ARGUMENT", "Exemption not found with ID: 99999")
	status, _, b = e.do("DELETE", fmt.Sprintf("/api/recurring-off-days/%d/exemptions/%d", id, exemptionID),
		e.testTok, nil)
	if status != 204 || len(b) != 0 {
		t.Fatal(status, string(b))
	}
	// Deleting the rule cascades to its exemptions.
	e.do("DELETE", fmt.Sprintf("/api/recurring-off-days/%d", id), e.testTok, nil)
	var n int
	if err := h.Pool.QueryRow(context.Background(), `SELECT count(*) FROM recurring_off_day_exemptions`).
		Scan(&n); err != nil || n != 0 {
		t.Fatal(n, err)
	}
}

func rule(pattern domain.RecurrencePattern, weekday int, interval, week *int, ref *time.Time) *recurringoffdays.RecurringOffDay {
	return &recurringoffdays.RecurringOffDay{ID: 1, Pattern: pattern, Weekday: weekday, WeekInterval: interval,
		WeekOfMonth: week, ReferenceDate: ref, StartDate: date(2025, 1, 1), IsActive: true}
}

func TestEvaluator(t *testing.T) {
	two, one, three, five, four := 2, 1, 3, 5, 4
	ref := date(2025, 1, 6)
	nth := rule(domain.EveryNthWeek, 1, &two, nil, &ref)
	for day, want := range map[time.Time]bool{date(2025, 1, 6): true, date(2025, 1, 13): false,
		date(2025, 1, 20): true, date(2025, 1, 7): false, date(2024, 12, 30): false} {
		if got := recurringoffdays.AppliesToDate(nth, day); got != want {
			t.Fatal(day, got)
		}
	}
	// A weekday before the reference weekday truncates negative week counts toward zero.
	wed := rule(domain.EveryNthWeek, 3, &one, nil, &ref)
	if !recurringoffdays.AppliesToDate(wed, date(2025, 1, 8)) || !recurringoffdays.AppliesToDate(wed, date(2025, 1, 1)) {
		t.Fatal("truncation")
	}
	third := rule(domain.NthWeekdayOfMonth, 1, nil, &three, nil)
	last := rule(domain.NthWeekdayOfMonth, 1, nil, &five, nil)
	if !recurringoffdays.AppliesToDate(third, date(2025, 1, 20)) || recurringoffdays.AppliesToDate(third, date(2025, 1, 27)) ||
		!recurringoffdays.AppliesToDate(last, date(2025, 1, 27)) || recurringoffdays.AppliesToDate(last, date(2025, 1, 20)) {
		t.Fatal("nth weekday")
	}
	fourth := rule(domain.NthWeekdayOfMonth, 1, nil, &four, nil)
	end := date(2025, 3, 1)
	fourth.EndDate = &end
	if !recurringoffdays.AppliesToDate(fourth, date(2025, 1, 27)) || recurringoffdays.AppliesToDate(fourth, date(2025, 3, 24)) {
		t.Fatal("end date")
	}
	fourth.EndDate = nil
	fourth.IsActive = false
	if recurringoffdays.AppliesToDate(fourth, date(2025, 1, 27)) {
		t.Fatal("inactive")
	}
	exempt := recurringoffdays.AppliesToDateWithExemptions(third, date(2025, 1, 20),
		func(int64, time.Time) bool { return true })
	if exempt {
		t.Fatal("exempt")
	}
}
