package reports_test

import (
	"bytes"
	"context"
	"fmt"
	"os"
	"strings"
	"testing"
	"time"

	"github.com/steffbo/timetrack/backend-go/internal/domain"
	"github.com/steffbo/timetrack/backend-go/internal/reports"
	"github.com/steffbo/timetrack/backend-go/internal/testutil"
	te "github.com/steffbo/timetrack/backend-go/internal/timeentries"
)

var h *testutil.Harness
var ctx = context.Background()

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
func date(s string) time.Time {
	v, err := time.Parse(time.DateOnly, s)
	if err != nil {
		panic(err)
	}
	return v
}
func setup(t *testing.T) (reports.Service, int64) {
	t.Helper()
	h.Reset(t)
	uid := h.User(t, "report@test.local", domain.User, nil)
	_, err := h.Pool.Exec(ctx, `INSERT INTO working_hours(user_id,weekday,hours,is_working_day)
 SELECT $1,n,8,n<6 FROM generate_series(1,7)n`, uid)
	check(t, err)
	return reports.Service{Pool: h.Pool}, uid
}
func save(t *testing.T, uid int64, d time.Time, inHour, outHour int, breakMinutes int,
	notes *string,
) te.Entry {
	t.Helper()
	in := d.Add(time.Duration(inHour) * time.Hour)
	var out *time.Time
	if outHour >= 0 {
		out = ptr(d.Add(time.Duration(outHour) * time.Hour))
	}
	e, err := (te.Repository{DB: h.Pool}).Save(ctx, te.Entry{UserID: uid, EntryDate: d,
		ClockIn: in, ClockOut: out,
		BreakMinutes: breakMinutes, Notes: notes, EntryType: "WORK"}, d)
	check(t, err)
	return e
}
func TestReportTimezoneBreaksActiveAndDateGrouping(t *testing.T) {
	s, uid := setup(t)
	save(t, uid, date("2025-01-06"), 8, 12, 15, ptr("first"))
	save(t, uid, date("2025-01-06"), 13, 17, 30, ptr("second"))
	save(t, uid, date("2025-01-07"), 8, 12, 0, nil)
	save(t, uid, date("2025-01-07"), 13, -1, 15, nil)
	// Late UTC entries remain grouped by entry_date, although Berlin's clock is on the next date.
	save(t, uid, date("2025-01-08"), 23, 25, 0, nil)
	entries, err := s.Entries(ctx, uid, 2025, 1)
	check(t, err)
	if len(entries) != 31 {
		t.Fatal(len(entries))
	}
	e := entries[5]
	if e.StartTime.Format("15:04") != "09:00" || e.EndTime.Format("15:04") != "18:00" ||
		e.BreakMinutes != 45 || *e.TotalHours != 7.25 || *e.ExpectedHours != 8 || *e.Overtime != -.75 ||
		e.Notes == nil || *e.Notes != "first" {
		t.Fatal(e)
	}
	e = entries[6]
	if e.StartTime == nil || e.StartTime.Format("15:04") != "09:00" || e.EndTime != nil ||
		e.TotalHours != nil || e.Overtime != nil || e.BreakMinutes != 15 {
		t.Fatal(e)
	}
	if entries[7].StartTime.Format("15:04") != "00:00" || entries[7].EndTime.Format("15:04") != "02:00" {
		t.Fatal(entries[7])
	}
	save(t, uid, date("2025-07-07"), 8, 17, 60, nil)
	entries, err = s.Entries(ctx, uid, 2025, 7)
	check(t, err)
	if entries[6].StartTime.Format("15:04") != "10:00" || entries[6].EndTime.Format("15:04") != "19:00" {
		t.Fatal(entries[6])
	}
}
func TestReportDayTypesNotesPriorityAndExemptions(t *testing.T) {
	s, uid := setup(t)
	var rule int64
	err := h.Pool.QueryRow(ctx, `INSERT INTO recurring_off_days
 (user_id,recurrence_pattern,weekday,week_interval,reference_date,start_date)
 VALUES($1,'EVERY_NTH_WEEK',1,1,'2025-01-01','2025-01-01') RETURNING id`, uid).Scan(&rule)
	check(t, err)
	_, err = h.Pool.Exec(ctx, `INSERT INTO recurring_off_day_exemptions(recurring_off_day_id,exemption_date)
 VALUES($1,'2025-01-13')`, rule)
	check(t, err)
	for _, tc := range []struct {
		day, kind string
		note      *string
	}{
		{"2025-01-06", "VACATION", ptr("time off wins")}, {"2025-01-07", "VACATION", nil},
		{"2025-01-07", "SICK", nil}, {"2025-01-08", "CHILD_SICK", nil},
		{"2025-01-09", "PUBLIC_HOLIDAY", nil}, {"2025-01-10", "EDUCATION", nil},
		{"2025-01-11", "PERSONAL", nil},
	} {
		_, err = h.Pool.Exec(ctx, `INSERT INTO time_off(user_id,start_date,end_date,time_off_type,notes)
   VALUES($1,$2,$2,$3,$4)`, uid, date(tc.day), tc.kind, tc.note)
		check(t, err)
	}
	save(t, uid, date("2025-01-06"), 8, 17, 0, ptr("entry loses"))
	save(t, uid, date("2025-01-07"), 8, 17, 0, ptr("entry used when off note null"))
	entries, err := s.Entries(ctx, uid, 2025, 1)
	check(t, err)
	for _, tc := range []struct {
		day  int
		kind string
	}{
		{6, "VACATION"}, {7, "SICK"}, {8, "SICK"}, {9, "PUBLIC_HOLIDAY"}, {10, "REGULAR"}, {11, "REGULAR"},
		{12, "WEEKEND"}, {13, "REGULAR"}, {20, "RECURRING_OFF_DAY"},
	} {
		if entries[tc.day-1].DayType != tc.kind {
			t.Fatal(tc, entries[tc.day-1])
		}
	}
	if *entries[5].Notes != "time off wins" || *entries[6].Notes != "entry used when off note null" {
		t.Fatal(entries)
	}
}
func TestCSVExactBytesAndDistinctExpectedTotals(t *testing.T) {
	start, end := date("2025-03-03").Add(9*time.Hour), date("2025-03-03").Add(17*time.Hour)
	entries := []reports.DailyReportEntry{
		{Date: date("2025-03-03"), StartTime: &start, EndTime: &end, BreakMinutes: 30,
			TotalHours: ptr(7.5), ExpectedHours: ptr(8.0), Overtime: ptr(-.5), DayType: "REGULAR"},
		{Date: date("2025-03-04"), ExpectedHours: ptr(8.0), DayType: "VACATION", TimeOffType: "VACATION"},
	}
	user := reports.User{FirstName: ptr("Jörg"), LastName: ptr("Müller")}
	expected := "\uFEFFStundenzettel März 2025\nMitarbeiter;Jörg Müller\n\n" +
		"Datum;Anfang;Pause (min);Ende;Gesamt;Überstunden\n" +
		"03.03.2025;09:00;30;17:00;7,50;-0,50\n04.03.2025;;0;;;\n" +
		"\nZusammenfassung\nGesamtstunden;7,50\nSollstunden;16,00\nGesamtüberstunden;-0,50\n"
	got := reports.GenerateCSV(2025, 3, user, entries)
	if string(got) != expected {
		t.Fatalf("got %q\nwant %q", got, expected)
	}
	partial := reports.GenerateCSV(2025, 3, reports.User{}, nil)
	if !bytes.Contains(partial, []byte("Mitarbeiter;null null\n")) ||
		!bytes.Contains(partial, []byte("Gesamtüberstunden;+0,00\n")) {
		t.Fatal(string(partial))
	}
	pdf, err := reports.GeneratePDF(2025, 3, user, entries, false)
	check(t, err)
	for _, text := range [][]byte{[]byte("Stundenzettel M\xe4rz 2025"), []byte("J\xf6rg M\xfcller"),
		[]byte("Sollstunden: 8h 0m"), []byte("Gesamtstunden: 7h 30m"), []byte("Gesamt\xfcberstunden: -30 min"),
		[]byte("Urlaub"), []byte("30 min")} {
		if !bytes.Contains(pdf, text) {
			t.Fatalf("missing %q", text)
		}
	}
	if !bytes.HasPrefix(pdf, []byte("%PDF-1.")) || !bytes.Contains(pdf, []byte("xref\n")) ||
		!bytes.HasSuffix(bytes.TrimSpace(pdf), []byte("%%EOF")) {
		t.Fatal("invalid PDF envelope")
	}
}
func TestPDFProductionAndReportValidation(t *testing.T) {
	s, uid := setup(t)
	pdf, err := s.PDF(ctx, uid, 2025, 12)
	check(t, err)
	if len(pdf) < 1000 || !bytes.HasPrefix(pdf, []byte("%PDF-")) {
		t.Fatal(len(pdf))
	}
	for _, tc := range []struct {
		year, month int
		message     string
	}{
		{1999, 1, "Year must be between 2000 and 2100"}, {2101, 1, "Year must be between 2000 and 2100"},
		{2025, 0, "Month must be between 1 and 12"}, {2025, 13, "Month must be between 1 and 12"},
	} {
		_, err = s.PDF(ctx, uid, tc.year, tc.month)
		if err == nil || err.Error() != tc.message {
			t.Fatal(tc, err)
		}
	}
	_, err = s.PDF(ctx, 99999, 2025, 1)
	if err == nil || err.Error() != "User not found: 99999" {
		t.Fatal(err)
	}
	csv, err := s.CSV(ctx, uid, 2025, 1, reports.User{})
	check(t, err)
	if !strings.HasPrefix(string(csv), "\uFEFFStundenzettel Januar 2025\nMitarbeiter;null null") {
		t.Fatal(string(csv))
	}
}
