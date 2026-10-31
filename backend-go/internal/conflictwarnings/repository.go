// Package conflictwarnings ports recurring off-day conflict warnings and the conflict detector.
package conflictwarnings

import (
	"context"
	"errors"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/steffbo/timetrack/backend-go/internal/db"
)

// Warning mirrors RecurringOffDayConflictWarning; dates are UTC midnight values.
type Warning struct {
	ID, UserID        int64
	ConflictDate      time.Time
	TimeEntryID       *int64
	RecurringOffDayID *int64
	Acknowledged      bool
	AcknowledgedAt    *time.Time
	CreatedAt         time.Time
	UpdatedAt         time.Time
}

const columns = `id,user_id,conflict_date,time_entry_id,recurring_off_day_id,acknowledged,acknowledged_at,
 created_at,updated_at`

func scan(row pgx.Row) (*Warning, error) {
	var w Warning
	err := row.Scan(&w.ID, &w.UserID, &w.ConflictDate, &w.TimeEntryID, &w.RecurringOffDayID,
		&w.Acknowledged, &w.AcknowledgedAt, &w.CreatedAt, &w.UpdatedAt)
	return &w, err
}

func list(ctx context.Context, q db.Querier, sql string, args ...any) ([]Warning, error) {
	rows, err := q.Query(ctx, sql, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	out := []Warning{}
	for rows.Next() {
		w, err := scan(rows)
		if err != nil {
			return nil, err
		}
		out = append(out, *w)
	}
	return out, rows.Err()
}

// FindByID returns nil when the warning does not exist.
func FindByID(ctx context.Context, q db.Querier, id int64) (*Warning, error) {
	w, err := scan(q.QueryRow(ctx, `SELECT `+columns+
		` FROM recurring_off_day_conflict_warnings WHERE id=$1`, id))
	if errors.Is(err, pgx.ErrNoRows) {
		return nil, nil
	}
	return w, err
}

// FindByUserIDOrderByConflictDateDesc lists all warnings of a user.
func FindByUserIDOrderByConflictDateDesc(ctx context.Context, q db.Querier, userID int64) ([]Warning, error) {
	return list(ctx, q, `SELECT `+columns+` FROM recurring_off_day_conflict_warnings
 WHERE user_id=$1 ORDER BY conflict_date DESC`, userID)
}

// FindUnacknowledgedByUserID lists unacknowledged warnings, newest conflict first.
func FindUnacknowledgedByUserID(ctx context.Context, q db.Querier, userID int64) ([]Warning, error) {
	return list(ctx, q, `SELECT `+columns+` FROM recurring_off_day_conflict_warnings
 WHERE user_id=$1 AND acknowledged=false ORDER BY conflict_date DESC`, userID)
}

// FindByUserIDAndDateRange is findByUserIdAndDateRange (inclusive bounds).
func FindByUserIDAndDateRange(
	ctx context.Context, q db.Querier, userID int64, start, end time.Time,
) ([]Warning, error) {
	return list(ctx, q, `SELECT `+columns+` FROM recurring_off_day_conflict_warnings
 WHERE user_id=$1 AND conflict_date>=$2 AND conflict_date<=$3 ORDER BY conflict_date DESC`,
		userID, start, end)
}

// FindByUserIDAndConflictDate returns nil when no warning exists for the day.
func FindByUserIDAndConflictDate(
	ctx context.Context, q db.Querier, userID int64, day time.Time,
) (*Warning, error) {
	w, err := scan(q.QueryRow(ctx, `SELECT `+columns+` FROM recurring_off_day_conflict_warnings
 WHERE user_id=$1 AND conflict_date=$2`, userID, day))
	if errors.Is(err, pgx.ErrNoRows) {
		return nil, nil
	}
	return w, err
}

// ExistsByUserIDAndConflictDate is existsByUserIdAndConflictDate.
func ExistsByUserIDAndConflictDate(ctx context.Context, q db.Querier, userID int64, day time.Time) (bool, error) {
	var ok bool
	err := q.QueryRow(ctx, `SELECT EXISTS(SELECT 1 FROM recurring_off_day_conflict_warnings
 WHERE user_id=$1 AND conflict_date=$2)`, userID, day).Scan(&ok)
	return ok, err
}

// Insert saves a new warning and fills ID and timestamps.
func Insert(ctx context.Context, q db.Querier, w *Warning, now time.Time) error {
	w.CreatedAt, w.UpdatedAt = now, now
	return q.QueryRow(ctx, `INSERT INTO recurring_off_day_conflict_warnings (user_id,conflict_date,time_entry_id,
 recurring_off_day_id,acknowledged,acknowledged_at,created_at,updated_at) VALUES($1,$2,$3,$4,$5,$6,$7,$7)
 RETURNING id`, w.UserID, w.ConflictDate, w.TimeEntryID, w.RecurringOffDayID, w.Acknowledged,
		w.AcknowledgedAt, now).Scan(&w.ID)
}

// Acknowledge marks the warning acknowledged at the given wall time (RecurringOffDayConflictWarning.acknowledge).
func Acknowledge(ctx context.Context, q db.Querier, w *Warning, now time.Time) error {
	w.Acknowledged, w.AcknowledgedAt, w.UpdatedAt = true, &now, now
	_, err := q.Exec(ctx, `UPDATE recurring_off_day_conflict_warnings
 SET acknowledged=true,acknowledged_at=$2,updated_at=$2 WHERE id=$1`, w.ID, now)
	return err
}

// DeleteByTimeEntryID is deleteByTimeEntryId.
func DeleteByTimeEntryID(ctx context.Context, q db.Querier, timeEntryID int64) error {
	_, err := q.Exec(ctx, `DELETE FROM recurring_off_day_conflict_warnings WHERE time_entry_id=$1`, timeEntryID)
	return err
}
