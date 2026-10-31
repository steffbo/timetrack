package timeentries_test

import (
	"encoding/json"
	"fmt"
	"net/http"
	"strings"
	"testing"
	"time"

	"github.com/steffbo/timetrack/backend-go/internal/api"
	"github.com/steffbo/timetrack/backend-go/internal/clock"
	"github.com/steffbo/timetrack/backend-go/internal/domain"
	"github.com/steffbo/timetrack/backend-go/internal/testutil"
)

func TestHTTPContract(t *testing.T) {
	h.Reset(t)
	pw := "password"
	uid := h.User(t, "http@test.local", domain.User, &pw)
	_, err := h.Pool.Exec(ctx, `INSERT INTO working_hours(user_id,weekday,hours,is_working_day)
 SELECT $1,n,8,n<6 FROM generate_series(1,7)n`, uid)
	check(t, err)
	_, srv := h.Server(t, clock.Fixed{Time: now})
	session := testutil.Login(t, srv.URL, "http@test.local", pw)
	request := func(method, path string, body any, status int) []byte {
		t.Helper()
		got, b := testutil.Request(t, srv.URL, method, path, session.AccessToken, body)
		if got != status {
			t.Fatalf("%s %s: got %d want %d: %s", method, path, got, status, b)
		}
		return b
	}
	b := request("GET", "/api/time-entries", nil, 200)
	if strings.TrimSpace(string(b)) != "[]" {
		t.Fatal(string(b))
	}
	req := api.CreateTimeEntryRequest{ClockIn: day().Add(8 * time.Hour), ClockOut: day().Add(17 * time.Hour),
		EntryType: "WORK", BreakMinutes: ptr(30)}
	b = request("POST", "/api/time-entries", req, 201)
	var entry api.TimeEntryResponse
	check(t, json.Unmarshal(b, &entry))
	if *entry.HoursWorked != 8.5 || entry.IsActive || entry.UserId != uid ||
		!strings.Contains(string(b), `"notes":null`) {
		t.Fatal(string(b))
	}
	b = request("POST", "/api/time-entries", req, 400)
	if !strings.Contains(string(b), `"code":"INVALID_ARGUMENT"`) ||
		!strings.Contains(string(b), "Time entry overlaps with existing entry from "+
			req.ClockIn.In(time.Local).Format("2006-01-02T15:04")+
			" to "+req.ClockOut.In(time.Local).Format("2006-01-02T15:04")) {
		t.Fatal(string(b))
	}
	req.ClockOut = req.ClockIn.Add(-time.Hour)
	b = request("POST", "/api/time-entries", req, 400)
	if !strings.Contains(string(b), "Clock out time must be after clock in time") {
		t.Fatal(string(b))
	}
	request("GET", "/api/time-entries?startDate=2040-01-01", nil, 200) // A single filter is ignored.
	b = request("GET", "/api/time-entries/daily-summary?startDate=2030-01-14&endDate=2030-01-14", nil, 200)
	var summaries []api.DailySummaryResponse
	check(t, json.Unmarshal(b, &summaries))
	if len(summaries) != 1 || summaries[0].ActualHours != 8.5 || summaries[0].ExpectedHours != 8 ||
		summaries[0].Status != "ABOVE_EXPECTED" || summaries[0].TimeOffEntries == nil ||
		summaries[0].RecurringOffDays == nil || summaries[0].ConflictWarning != nil {
		t.Fatal(string(b))
	}
	b = request("GET", "/api/time-entries/daily-summary?startDate=2040-01-01", nil, 200)
	check(t, json.Unmarshal(b, &summaries))
	if len(summaries) != 31 || summaries[0].Date.Format(time.DateOnly) != "2030-01-01" {
		t.Fatal(string(b))
	}
	request("GET", "/api/time-entries/daily-summary?startDate=2030-02-01&endDate=2030-01-01", nil, 200)
	url := fmt.Sprintf("/api/time-entries/%d", entry.Id)
	update := api.UpdateTimeEntryRequest{ClockIn: req.ClockIn, ClockOut: ptr(req.ClockIn.Add(-time.Hour)),
		EntryType: "WORK"}
	b = request("PUT", url, update, 500)
	if !strings.Contains(string(b), `"details":"Checkout-Zeit muss nach der Checkin-Zeit liegen."`) {
		t.Fatal(string(b))
	}
	request("DELETE", url, nil, 204)
	request("DELETE", url, nil, 400)
	request("POST", "/api/time-entries/clock-out", api.ClockOutRequest{}, 500)
	request("POST", "/api/time-entries/clock-in", api.ClockInRequest{}, 201)
	b = request("POST", "/api/time-entries/clock-in", api.ClockInRequest{}, 500)
	if !strings.Contains(string(b), "Bereits eingecheckt. Bitte zuerst auschecken.") {
		t.Fatal(string(b))
	}
	req.ClockOut = req.ClockIn.Add(time.Hour)
	b = request("POST", "/api/time-entries", req, 400)
	if !strings.Contains(string(b), "Cannot create manual entry while an active session exists.") {
		t.Fatal(string(b))
	}
	request("GET", "/api/public-holidays", nil, 200)
	b = request("GET", "/api/time-entries/monthly-report?year=1999&month=1", nil, 400)
	if !strings.Contains(string(b), "Year must be between 2000 and 2100") {
		t.Fatal(string(b))
	}
	reqHTTP, err := http.NewRequest("GET", srv.URL+"/api/time-entries/monthly-report?year=2030&month=1", nil)
	check(t, err)
	reqHTTP.Header.Set("Authorization", "Bearer "+session.AccessToken)
	res, err := http.DefaultClient.Do(reqHTTP)
	check(t, err)
	defer res.Body.Close()
	if res.StatusCode != 200 || res.Header.Get("Content-Type") != "application/pdf" ||
		res.Header.Get("Content-Disposition") != `attachment; filename="stundenzettel_2030_01.pdf"` {
		t.Fatal(res)
	}
}

func TestSummaryTimeOffRecurrenceExemptionsAndConflictCleanup(t *testing.T) {
	s, r, uid, _ := setup(t)
	var rule int64
	err := h.Pool.QueryRow(ctx, `INSERT INTO recurring_off_days
 (user_id,recurrence_pattern,weekday,week_interval,reference_date,start_date,description)
 VALUES($1,'EVERY_NTH_WEEK',1,1,$2,$2,'Freier Montag') RETURNING id`, uid, day()).Scan(&rule)
	check(t, err)
	created, err := s.Create(ctx, uid, api.CreateTimeEntryRequest{ClockIn: day().Add(8 * time.Hour),
		ClockOut: day().Add(16 * time.Hour), EntryType: "WORK"})
	check(t, err)
	v := summary(t, s, uid, day(), day())[0]
	if len(*v.RecurringOffDays) != 1 || v.ConflictWarning == nil ||
		v.ConflictWarning.RecurringOffDayDescription == nil || *v.ConflictWarning.TimeEntryId != created.ID {
		t.Fatal(v)
	}
	// A second entry on the same date retains the first conflict warning.
	completed(t, r, uid, day(), 2)
	check(t, s.Delete(ctx, uid, created.ID))
	if summary(t, s, uid, day(), day())[0].ConflictWarning != nil {
		t.Fatal("warning not cascaded")
	}
	_, err = h.Pool.Exec(ctx, `INSERT INTO recurring_off_day_exemptions(recurring_off_day_id,exemption_date)
 VALUES($1,$2)`, rule, day())
	check(t, err)
	if v := summary(t, s, uid, day(), day())[0]; len(*v.RecurringOffDays) != 0 {
		t.Fatal(v)
	}
	// Multi-day time-off days use the full absence, not just this summary's range.
	_, err = h.Pool.Exec(ctx, `INSERT INTO time_off(user_id,start_date,end_date,time_off_type)
 VALUES($1,$2,$3,'VACATION')`, uid, day(), day().AddDate(0, 0, 2))
	check(t, err)
	v = summary(t, s, uid, day(), day())[0]
	if len(*v.TimeOffEntries) != 1 || (*v.TimeOffEntries)[0].Days != 3 {
		t.Fatal(v)
	}
	_, err = h.Pool.Exec(ctx, `INSERT INTO time_off(user_id,start_date,end_date,time_off_type)
 VALUES($1,$2,$2,'SICK')`, uid, day().AddDate(0, 0, 1))
	check(t, err)
	if v := summary(t, s, uid, day(), day())[0]; (*v.TimeOffEntries)[0].Days != 2 {
		t.Fatal(v)
	}
}

func TestClockOutCreatesConflictAndPreservesNullNotes(t *testing.T) {
	s, _, uid, _ := setup(t)
	_, err := h.Pool.Exec(ctx, `INSERT INTO recurring_off_days
 (user_id,recurrence_pattern,weekday,week_interval,reference_date,start_date)
 VALUES($1,'EVERY_NTH_WEEK',1,1,$2,$2)`, uid, day())
	check(t, err)
	_, err = s.ClockIn(ctx, uid, ptr("keep notes"))
	check(t, err)
	s.Clock = clock.Fixed{Time: now.Add(time.Hour)}
	e, err := s.ClockOut(ctx, uid, nil)
	check(t, err)
	if e.Notes == nil || *e.Notes != "keep notes" {
		t.Fatal(e)
	}
	if summary(t, s, uid, day(), day())[0].ConflictWarning == nil {
		t.Fatal("missing warning")
	}
}

func TestFailedPersistenceRollsBackAndUpdateDoesNotDetectConflicts(t *testing.T) {
	s, r, uid, _ := setup(t)
	req := api.CreateTimeEntryRequest{ClockIn: day().Add(8 * time.Hour), ClockOut: day().Add(8 * time.Hour),
		EntryType: "WORK"}
	_, err := s.Create(ctx, uid, req)
	if err == nil {
		t.Fatal("equal times must fail database check as Java does")
	}
	if len(list(t, s, uid, nil, nil)) != 0 {
		t.Fatal("transaction not rolled back")
	}
	e := completed(t, r, uid, day(), 8)
	// Java UpdateTimeEntryUseCase deliberately does not call the detector or overlap query.
	_, err = h.Pool.Exec(ctx, `INSERT INTO recurring_off_days
 (user_id,recurrence_pattern,weekday,week_interval,reference_date,start_date)
 VALUES($1,'EVERY_NTH_WEEK',1,1,$2,$2)`, uid, day())
	check(t, err)
	_, err = s.Update(ctx, uid, e.ID, updateRequest(e))
	check(t, err)
	if summary(t, s, uid, day(), day())[0].ConflictWarning != nil {
		t.Fatal("unexpected warning")
	}
}

func TestSummaryWorkingDaysPrecedenceAndHalfDays(t *testing.T) {
	s, _, uid, _ := setup(t)
	_, err := h.Pool.Exec(ctx, `UPDATE users SET half_day_holidays_enabled=true WHERE id=$1`, uid)
	check(t, err)
	_, err = h.Pool.Exec(ctx, `INSERT INTO recurring_off_days
 (user_id,recurrence_pattern,weekday,week_interval,reference_date,start_date)
 VALUES($1,'EVERY_NTH_WEEK',1,1,'2025-01-01','2025-01-01')`, uid)
	check(t, err)
	for _, kind := range []string{"VACATION", "SICK"} {
		_, err = h.Pool.Exec(ctx, `INSERT INTO time_off(user_id,start_date,end_date,time_off_type)
  VALUES($1,'2025-12-22','2025-12-26',$2)`, uid, kind)
		check(t, err)
	}
	// Vacation loses Dec 22 to the recurring rule, and all covered days to sickness.
	// Sickness ignores recurring rules; Dec 24 counts half, Dec 25/26 are holidays.
	start := time.Date(2025, 12, 22, 0, 0, 0, 0, time.UTC)
	v := summary(t, s, uid, start, start)[0]
	if len(*v.TimeOffEntries) != 2 {
		t.Fatal(v)
	}
	for _, o := range *v.TimeOffEntries {
		want := 0.0
		if o.TimeOffType == "SICK" {
			want = 2.5
		}
		if o.Days != want {
			t.Fatal(o, want)
		}
	}
}

func TestSummaryToleranceActiveAndMissingConfiguration(t *testing.T) {
	s, r, uid, _ := setup(t)
	for _, tc := range []struct {
		hours  float64
		status string
	}{
		{8.0, "MATCHED"}, {8.05, "MATCHED"}, {7.95, "MATCHED"}, {7.8, "BELOW_EXPECTED"}, {8.2, "ABOVE_EXPECTED"},
	} {
		e := completed(t, r, uid, day(), tc.hours)
		v := summary(t, s, uid, day(), day())[0]
		if v.Status != api.DailySummaryResponseStatus(tc.status) {
			t.Fatal(tc, v)
		}
		check(t, s.Delete(ctx, uid, e.ID))
	}
	_, err := s.ClockIn(ctx, uid, nil)
	check(t, err)
	v := summary(t, s, uid, day(), day())[0]
	if v.ActualHours != 0 || v.Status != "NO_ENTRY" || len(*v.Entries) != 1 {
		t.Fatal(v)
	}
	_, err = h.Pool.Exec(ctx, `DELETE FROM working_hours WHERE user_id=$1`, uid)
	check(t, err)
	_, err = s.Summary(ctx, uid, day(), day())
	requireError(t, err, fmt.Sprintf("Keine Arbeitszeitkonfiguration gefunden für Benutzer: %d", uid))
}
