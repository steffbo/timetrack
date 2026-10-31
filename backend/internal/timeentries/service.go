package timeentries

import (
	"context"
	"errors"
	"fmt"
	"math"
	"sort"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	openapi_types "github.com/oapi-codegen/runtime/types"
	"github.com/steffbo/timetrack/backend/internal/api"
	"github.com/steffbo/timetrack/backend/internal/clock"
	"github.com/steffbo/timetrack/backend/internal/conflictwarnings"
	"github.com/steffbo/timetrack/backend/internal/db"
	"github.com/steffbo/timetrack/backend/internal/httpx"
)

type Service struct {
	Pool  *pgxpool.Pool
	Clock clock.Clock
}

func (s Service) List(ctx context.Context, uid int64, start, end *time.Time) ([]Entry, error) {
	entries, err := (Repository{s.Pool}).List(ctx, uid, start, end)
	sort.SliceStable(entries, func(i, j int) bool { return entries[i].ClockIn.After(entries[j].ClockIn) })
	return entries, err
}
func (s Service) ClockIn(ctx context.Context, uid int64, notes *string) (out Entry, err error) {
	err = db.Transaction(ctx, s.Pool, func(tx pgx.Tx) error {
		r := Repository{tx}
		_, e := r.Active(ctx, uid)
		if e == nil {
			return errors.New("Bereits eingecheckt. Bitte zuerst auschecken.")
		}
		if !errors.Is(e, pgx.ErrNoRows) {
			return e
		}
		now := clock.LocalTimestamp(s.Clock.Now())
		entry := Entry{UserID: uid, EntryDate: now, ClockIn: now, EntryType: "WORK", Notes: notes}
		out, e = r.Save(ctx, entry, s.Clock.Now())
		return e
	})
	return
}
func (s Service) ClockOut(ctx context.Context, uid int64, notes *string) (out Entry, err error) {
	err = db.Transaction(ctx, s.Pool, func(tx pgx.Tx) error {
		r := Repository{tx}
		entry, e := r.Active(ctx, uid)
		if errors.Is(e, pgx.ErrNoRows) {
			return errors.New("Nicht eingecheckt. Bitte zuerst einchecken.")
		}
		if e != nil {
			return e
		}
		now := clock.LocalTimestamp(s.Clock.Now())
		entry.ClockOut = &now
		if notes != nil {
			entry.Notes = notes
		}
		out, e = r.Save(ctx, entry, s.Clock.Now())
		if e != nil {
			return e
		}
		return s.detectConflict(ctx, tx, out)
	})
	return
}
func (s Service) Create(ctx context.Context, uid int64, r api.CreateTimeEntryRequest) (out Entry, err error) {
	in, end := clock.LocalTimestamp(r.ClockIn.In(time.Local)), clock.LocalTimestamp(r.ClockOut.In(time.Local))
	err = db.Transaction(ctx, s.Pool, func(tx pgx.Tx) error {
		if end.Before(in) {
			return httpx.InvalidArgument("Clock out time must be after clock in time")
		}
		repo := Repository{tx}
		entries, e := repo.List(ctx, uid, nil, nil)
		if e != nil {
			return e
		}
		for _, other := range entries {
			if other.ClockOut == nil {
				return httpx.InvalidArgument(
					"Cannot create manual entry while an active session exists. Please clock out first.")
			}
			if in.Before(*other.ClockOut) && end.After(other.ClockIn) {
				return httpx.InvalidArgument(
					"Time entry overlaps with existing entry from " + formatDateTime(other.ClockIn) +
						" to " + formatDateTime(*other.ClockOut))
			}
		}
		entry := Entry{UserID: uid, EntryDate: in, ClockIn: in, ClockOut: &end,
			EntryType: string(r.EntryType), Notes: r.Notes}
		if r.BreakMinutes != nil {
			entry.BreakMinutes = *r.BreakMinutes
		}
		out, e = repo.Save(ctx, entry, s.Clock.Now())
		if e != nil {
			return e
		}
		return s.detectConflict(ctx, tx, out)
	})
	return
}
func formatDateTime(t time.Time) string {
	if t.Nanosecond() == 0 && t.Second() == 0 {
		return t.Format("2006-01-02T15:04")
	}
	base := t.Format("2006-01-02T15:04:05")
	if t.Nanosecond() == 0 {
		return base
	}
	digits := 9
	if t.Nanosecond()%1000000 == 0 {
		digits = 3
	} else if t.Nanosecond()%1000 == 0 {
		digits = 6
	}
	return base + fmt.Sprintf(".%09d", t.Nanosecond())[:digits+1]
}
func owned(ctx context.Context, r Repository, uid, id int64, verb string) (Entry, error) {
	e, err := r.Find(ctx, id)
	if errors.Is(err, pgx.ErrNoRows) {
		return e, httpx.InvalidArgument(
			fmt.Sprintf("Zeiterfassungseintrag nicht gefunden: %d", id))
	}
	if err != nil {
		return e, err
	}
	if e.UserID != uid {
		return e, httpx.InvalidArgument("Sie haben keine Berechtigung, diesen Eintrag zu " + verb + ".")
	}
	return e, nil
}
func (s Service) Update(ctx context.Context, uid, id int64, r api.UpdateTimeEntryRequest) (
	out Entry, err error,
) {
	err = db.Transaction(ctx, s.Pool, func(tx pgx.Tx) error {
		repo := Repository{tx}
		e, err := owned(ctx, repo, uid, id, "bearbeiten")
		if err != nil {
			return err
		}
		in := clock.LocalTimestamp(r.ClockIn.In(time.Local))
		var end *time.Time
		if r.ClockOut != nil {
			t := clock.LocalTimestamp(r.ClockOut.In(time.Local))
			end = &t
		}
		if end != nil && end.Before(in) {
			return errors.New("Checkout-Zeit muss nach der Checkin-Zeit liegen.")
		}
		e.ClockIn, e.ClockOut, e.EntryDate, e.Notes, e.EntryType = in, end, in, r.Notes, string(r.EntryType)
		e.BreakMinutes = 0
		if r.BreakMinutes != nil {
			e.BreakMinutes = *r.BreakMinutes
		}
		out, err = repo.Save(ctx, e, s.Clock.Now())
		return err
	})
	return
}
func (s Service) Delete(ctx context.Context, uid, id int64) error {
	return db.Transaction(ctx, s.Pool, func(tx pgx.Tx) error {
		repo := Repository{tx}
		_, err := owned(ctx, repo, uid, id, "löschen")
		if err != nil {
			return err
		}
		if err := repo.Delete(ctx, id); err != nil {
			return err
		}
		(conflictwarnings.Detector{Clock: s.Clock}).CleanupForTimeEntry(ctx, tx, id)
		return nil
	})
}
func (s Service) detectConflict(ctx context.Context, q db.Querier, e Entry) error {
	_, err := (conflictwarnings.Detector{Clock: s.Clock}).DetectAndCreateIfNeeded(ctx, q,
		conflictwarnings.TimeEntryRef{ID: e.ID, UserID: e.UserID, EntryDate: e.EntryDate})
	return err
}
func (s Service) Summary(ctx context.Context, uid int64, start, end time.Time) (
	[]api.DailySummaryResponse, error,
) {
	c, err := LoadCalendar(ctx, s.Pool, uid)
	if err != nil {
		return nil, err
	}
	if len(c.Working) == 0 {
		return nil, fmt.Errorf("Keine Arbeitszeitkonfiguration gefunden für Benutzer: %d", uid)
	}
	entries, err := (Repository{s.Pool}).List(ctx, uid, &start, &end)
	if err != nil {
		return nil, err
	}
	ws, err := warnings(ctx, s.Pool, uid, start, end)
	if err != nil {
		return nil, err
	}
	out := []api.DailySummaryResponse{}
	for d := start; !d.After(end); d = d.AddDate(0, 0, 1) {
		dayEntries := []api.TimeEntryResponse{}
		off := []api.TimeOffResponse{}
		rules := []api.RecurringOffDayResponse{}
		actual := 0.0
		for _, e := range entries {
			if e.EntryDate.Format(time.DateOnly) == d.Format(time.DateOnly) {
				dayEntries = append(dayEntries, e.Response())
				if h := e.HoursWorked(); h != nil {
					actual += *h
				}
			}
		}
		for _, o := range c.TimeOff {
			if Covers(o, d) {
				off = append(off, o)
			}
		}
		for _, r := range c.Rules {
			if r.Applies(d) {
				rules = append(rules, r.Response)
			}
		}
		expected := c.Expected(d)
		status := "ABOVE_EXPECTED"
		if actual == 0 {
			status = "NO_ENTRY"
		} else if math.Abs(actual-expected) <= 0.1 {
			status = "MATCHED"
		} else if actual < expected {
			status = "BELOW_EXPECTED"
		}
		out = append(out, api.DailySummaryResponse{Date: openapi_types.Date{Time: d}, ActualHours: actual,
			ExpectedHours: expected, Status: api.DailySummaryResponseStatus(status), Entries: &dayEntries,
			TimeOffEntries: &off, RecurringOffDays: &rules, ConflictWarning: ws[d.Format(time.DateOnly)]})
	}
	return out, nil
}
