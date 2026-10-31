// Package leavestore owns time-off persistence, shared by leave and balance use cases.
package leavestore

import (
	"context"
	"errors"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/steffbo/timetrack/backend-go/internal/db"
	"github.com/steffbo/timetrack/backend-go/internal/domain"
)

// TimeOff mirrors the Java entity; dates are UTC midnight values.
type TimeOff struct {
	ID, UserID  int64
	StartDate   time.Time
	EndDate     time.Time
	Type        domain.TimeOffType
	HoursPerDay *float64
	Notes       *string
	Confirmed   bool
	CreatedAt   time.Time
	UpdatedAt   time.Time
}

const columns = `id,user_id,start_date,end_date,time_off_type,hours_per_day::float8,notes,confirmed,
 created_at,updated_at`

func scan(row pgx.Row) (*TimeOff, error) {
	var t TimeOff
	err := row.Scan(&t.ID, &t.UserID, &t.StartDate, &t.EndDate, &t.Type, &t.HoursPerDay, &t.Notes,
		&t.Confirmed, &t.CreatedAt, &t.UpdatedAt)
	return &t, err
}

func list(ctx context.Context, q db.Querier, sql string, args ...any) ([]TimeOff, error) {
	rows, err := q.Query(ctx, sql, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	out := []TimeOff{}
	for rows.Next() {
		t, err := scan(rows)
		if err != nil {
			return nil, err
		}
		out = append(out, *t)
	}
	return out, rows.Err()
}

// FindByID returns nil when the entry does not exist.
func FindByID(ctx context.Context, q db.Querier, id int64) (*TimeOff, error) {
	t, err := scan(q.QueryRow(ctx, `SELECT `+columns+` FROM time_off WHERE id=$1`, id))
	if errors.Is(err, pgx.ErrNoRows) {
		return nil, nil
	}
	return t, err
}

// FindByUserIDOrderByStartDateDesc is findByUserIdOrderByStartDateDesc.
func FindByUserIDOrderByStartDateDesc(ctx context.Context, q db.Querier, userID int64) ([]TimeOff, error) {
	return list(ctx, q, `SELECT `+columns+` FROM time_off WHERE user_id=$1
 ORDER BY start_date DESC, id`, userID)
}

// FindByUserIDAndDateRange is findByUserIdAndDateRange (overlap, no order defined in Java; id order here).
func FindByUserIDAndDateRange(
	ctx context.Context, q db.Querier, userID int64, start, end time.Time,
) ([]TimeOff, error) {
	return list(ctx, q, `SELECT `+columns+` FROM time_off
 WHERE user_id=$1 AND start_date<=$3 AND end_date>=$2 ORDER BY id`, userID, start, end)
}

// FindByUserIDAndTypeAndYear is findByUserIdAndTypeAndYear (overlap with the given bounds).
func FindByUserIDAndTypeAndYear(
	ctx context.Context, q db.Querier, userID int64, typ domain.TimeOffType, yearStart, yearEnd time.Time,
) ([]TimeOff, error) {
	return list(ctx, q, `SELECT `+columns+` FROM time_off
 WHERE user_id=$1 AND time_off_type=$2 AND start_date<=$4 AND end_date>=$3 ORDER BY id`,
		userID, typ, yearStart, yearEnd)
}

// Insert saves a new entry and fills ID and timestamps.
func Insert(ctx context.Context, q db.Querier, t *TimeOff, now time.Time) error {
	t.CreatedAt, t.UpdatedAt = now, now
	return q.QueryRow(ctx, `INSERT INTO time_off
 (user_id,start_date,end_date,time_off_type,hours_per_day,notes,
 confirmed,created_at,updated_at) VALUES($1,$2,$3,$4,$5::float8::numeric,$6,$7,$8,$8) RETURNING id`,
		t.UserID, t.StartDate, t.EndDate, t.Type, t.HoursPerDay, t.Notes, t.Confirmed, now).Scan(&t.ID)
}

// Update saves all mutable columns and refreshes updated_at.
func Update(ctx context.Context, q db.Querier, t *TimeOff, now time.Time) error {
	t.UpdatedAt = now
	_, err := q.Exec(ctx, `UPDATE time_off SET start_date=$2,end_date=$3,time_off_type=$4,
 hours_per_day=$5::float8::numeric,notes=$6,confirmed=$7,updated_at=$8 WHERE id=$1`,
		t.ID, t.StartDate, t.EndDate, t.Type, t.HoursPerDay, t.Notes, t.Confirmed, now)
	return err
}

// Delete removes one entry.
func Delete(ctx context.Context, q db.Querier, id int64) error {
	_, err := q.Exec(ctx, `DELETE FROM time_off WHERE id=$1`, id)
	return err
}

// FindByUserID reads all entries in persistence order for calendar consumers.
func FindByUserID(ctx context.Context, q db.Querier, userID int64) ([]TimeOff, error) {
	return list(ctx, q, `SELECT `+columns+` FROM time_off WHERE user_id=$1 ORDER BY id`, userID)
}
