// Package timeentries implements time-entry persistence and use cases.
package timeentries

import (
	"context"
	"time"

	"github.com/jackc/pgx/v5"
	openapi_types "github.com/oapi-codegen/runtime/types"
	"github.com/steffbo/timetrack/backend/internal/api"
	"github.com/steffbo/timetrack/backend/internal/clock"
	"github.com/steffbo/timetrack/backend/internal/db"
)

type Entry struct {
	ID, UserID           int64
	EntryDate, ClockIn   time.Time
	ClockOut             *time.Time
	BreakMinutes         int
	EntryType            string
	Notes                *string
	CreatedAt, UpdatedAt time.Time
}

func (e Entry) Duration() *time.Duration {
	if e.ClockOut == nil {
		return nil
	}
	d := e.ClockOut.Sub(e.ClockIn)
	return &d
}
func (e Entry) HoursWorked() *float64 {
	d := e.Duration()
	if d == nil {
		return nil
	}
	// Duration minutes truncate toward zero before subtracting breaks.
	hours := float64(int64(*d/time.Minute)-int64(e.BreakMinutes)) / 60
	return &hours
}
func (e Entry) IsWorkEntry() bool { return e.EntryType == "WORK" }

// localOffset follows LocalDateTime.atZone: choose the earlier instant in an
// autumn overlap and shift a nonexistent local time forward by the daylight-saving gap.
func localOffset(t time.Time) time.Time {
	wall := clock.LocalTimestamp(t)
	_, before := wall.Add(-24 * time.Hour).In(time.Local).Zone()
	_, after := wall.Add(24 * time.Hour).In(time.Local).Zone()
	var chosen time.Time
	for _, offset := range []int{before, after} {
		candidate := wall.Add(-time.Duration(offset) * time.Second).In(time.Local)
		if clock.LocalTimestamp(candidate).Equal(wall) && (chosen.IsZero() || candidate.Before(chosen)) {
			chosen = candidate
		}
	}
	if !chosen.IsZero() {
		return chosen
	}
	return wall.Add(-time.Duration(before) * time.Second).In(time.Local)
}
func (e Entry) Response() api.TimeEntryResponse {
	out := api.TimeEntryResponse{Id: e.ID, UserId: e.UserID, EntryDate: openapi_types.Date{Time: e.EntryDate},
		ClockIn: localOffset(e.ClockIn), BreakMinutes: &e.BreakMinutes,
		EntryType: api.TimeEntryResponseEntryType(e.EntryType), Notes: e.Notes,
		HoursWorked: e.HoursWorked(), IsActive: e.ClockOut == nil}
	created, updated := localOffset(e.CreatedAt), localOffset(e.UpdatedAt)
	out.CreatedAt, out.UpdatedAt = &created, &updated
	if e.ClockOut != nil {
		t := localOffset(*e.ClockOut)
		out.ClockOut = &t
	}
	return out
}

type Repository struct{ DB db.Querier }

const columns = `id,user_id,entry_date,clock_in,clock_out,break_minutes,entry_type,notes,
 created_at,updated_at`

func scan(row pgx.Row) (Entry, error) {
	var e Entry
	err := row.Scan(&e.ID, &e.UserID, &e.EntryDate, &e.ClockIn, &e.ClockOut, &e.BreakMinutes,
		&e.EntryType, &e.Notes, &e.CreatedAt, &e.UpdatedAt)
	return e, err
}
func (r Repository) Find(ctx context.Context, id int64) (Entry, error) {
	return scan(r.DB.QueryRow(ctx, `SELECT `+columns+` FROM time_entries WHERE id=$1`, id))
}
func (r Repository) List(ctx context.Context, uid int64, start, end *time.Time) ([]Entry, error) {
	rows, err := r.DB.Query(ctx, `SELECT `+columns+` FROM time_entries WHERE user_id=$1
 AND ($2::date IS NULL OR $3::date IS NULL OR entry_date BETWEEN $2 AND $3)`, uid, start, end)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	out := []Entry{}
	for rows.Next() {
		e, err := scan(rows)
		if err != nil {
			return nil, err
		}
		out = append(out, e)
	}
	return out, rows.Err()
}
func (r Repository) Active(ctx context.Context, uid int64) (Entry, error) {
	rows, err := r.DB.Query(ctx, `SELECT `+columns+` FROM time_entries
 WHERE user_id=$1 AND clock_out IS NULL`, uid)
	if err != nil {
		return Entry{}, err
	}
	defer rows.Close()
	if !rows.Next() {
		if rows.Err() != nil {
			return Entry{}, rows.Err()
		}
		return Entry{}, pgx.ErrNoRows
	}
	e, err := scan(rows)
	if err != nil {
		return e, err
	}
	if rows.Next() {
		return Entry{}, pgx.ErrTooManyRows
	}
	return e, rows.Err()
}
func (r Repository) Save(ctx context.Context, e Entry, now time.Time) (Entry, error) {
	now = clock.LocalTimestamp(now)
	if e.ID == 0 {
		return scan(r.DB.QueryRow(ctx, `INSERT INTO time_entries
   (user_id,entry_date,clock_in,clock_out,break_minutes,entry_type,notes,created_at,updated_at)
   VALUES($1,$2,$3,$4,$5,$6,$7,$8,$8) RETURNING `+columns,
			e.UserID, e.EntryDate, e.ClockIn, e.ClockOut, e.BreakMinutes, e.EntryType, e.Notes, now))
	}
	return scan(r.DB.QueryRow(ctx, `UPDATE time_entries SET entry_date=$2,clock_in=$3,clock_out=$4,
 break_minutes=$5,entry_type=$6,notes=$7,updated_at=$8 WHERE id=$1 RETURNING `+columns,
		e.ID, e.EntryDate, e.ClockIn, e.ClockOut, e.BreakMinutes, e.EntryType, e.Notes, now))
}
func (r Repository) Delete(ctx context.Context, id int64) error {
	_, err := r.DB.Exec(ctx, `DELETE FROM time_entries WHERE id=$1`, id)
	return err
}
func (r Repository) HasOverlappingEntries(ctx context.Context, uid int64, in time.Time,
	out *time.Time, exclude *int64) (bool, error) {
	var found bool
	err := r.DB.QueryRow(ctx, `SELECT EXISTS(SELECT 1 FROM time_entries WHERE user_id=$1
 AND ($4::bigint IS NULL OR id<>$4) AND (clock_out IS NULL OR $3::timestamp IS NULL
 OR (clock_in<$3 AND (clock_out IS NULL OR clock_out>$2))))`, uid, in, out, exclude).Scan(&found)
	return found, err
}
func (r Repository) TotalMinutes(ctx context.Context, uid int64, start, end time.Time) (int64, error) {
	var total int64
	err := r.DB.QueryRow(ctx, `SELECT COALESCE(SUM(trunc(extract(epoch FROM (clock_out-clock_in))/60)),0)::bigint
 FROM time_entries WHERE user_id=$1 AND entry_date BETWEEN $2 AND $3
 AND clock_out IS NOT NULL AND entry_type='WORK'`, uid, start, end).Scan(&total)
	return total, err
}
