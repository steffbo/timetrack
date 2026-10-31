// Package recurringoffdays ports the recurring off-day rules, exemptions and the pattern evaluator.
package recurringoffdays

import (
	"context"
	"errors"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/steffbo/timetrack/backend-go/internal/db"
	"github.com/steffbo/timetrack/backend-go/internal/domain"
)

// RecurringOffDay mirrors the Java entity; dates are UTC midnight values.
type RecurringOffDay struct {
	ID, UserID    int64
	Pattern       domain.RecurrencePattern
	Weekday       int
	WeekInterval  *int
	ReferenceDate *time.Time
	WeekOfMonth   *int
	StartDate     time.Time
	EndDate       *time.Time
	IsActive      bool
	Description   *string
	CreatedAt     time.Time
	UpdatedAt     time.Time
}

// Exemption mirrors RecurringOffDayExemption.
type Exemption struct {
	ID, RecurringOffDayID int64
	Date                  time.Time
	Reason                *string
	CreatedAt             time.Time
}

const rodColumns = `id,user_id,recurrence_pattern,weekday,week_interval,reference_date,week_of_month,
 start_date,end_date,is_active,description,created_at,updated_at`

func scanRod(row pgx.Row) (*RecurringOffDay, error) {
	var r RecurringOffDay
	err := row.Scan(&r.ID, &r.UserID, &r.Pattern, &r.Weekday, &r.WeekInterval, &r.ReferenceDate,
		&r.WeekOfMonth, &r.StartDate, &r.EndDate, &r.IsActive, &r.Description, &r.CreatedAt, &r.UpdatedAt)
	return &r, err
}

func queryRods(ctx context.Context, q db.Querier, sql string, args ...any) ([]RecurringOffDay, error) {
	rows, err := q.Query(ctx, sql, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	out := []RecurringOffDay{}
	for rows.Next() {
		r, err := scanRod(rows)
		if err != nil {
			return nil, err
		}
		out = append(out, *r)
	}
	return out, rows.Err()
}

// FindByID returns nil when the rule does not exist.
func FindByID(ctx context.Context, q db.Querier, id int64) (*RecurringOffDay, error) {
	r, err := scanRod(q.QueryRow(ctx, `SELECT `+rodColumns+` FROM recurring_off_days WHERE id=$1`, id))
	if errors.Is(err, pgx.ErrNoRows) {
		return nil, nil
	}
	return r, err
}

// FindByUserID is findByUserId (no defined order in Java; id order here).
func FindByUserID(ctx context.Context, q db.Querier, userID int64) ([]RecurringOffDay, error) {
	return queryRods(ctx, q, `SELECT `+rodColumns+
		` FROM recurring_off_days WHERE user_id=$1 ORDER BY id`, userID)
}

// FindByUserIDOrderByStartDateDesc is findByUserIdOrderByStartDateDesc.
func FindByUserIDOrderByStartDateDesc(ctx context.Context, q db.Querier, userID int64) ([]RecurringOffDay, error) {
	return queryRods(ctx, q, `SELECT `+rodColumns+
		` FROM recurring_off_days WHERE user_id=$1 ORDER BY start_date DESC, id`, userID)
}

// FindActiveByUserIDAndDate is findActiveByUserIdAndDate.
func FindActiveByUserIDAndDate(
	ctx context.Context, q db.Querier, userID int64, date time.Time,
) ([]RecurringOffDay, error) {
	return queryRods(ctx, q, `SELECT `+rodColumns+` FROM recurring_off_days WHERE user_id=$1
 AND is_active=true AND start_date<=$2 AND (end_date IS NULL OR end_date>=$2) ORDER BY id`, userID, date)
}

// Insert saves a new rule and fills ID and timestamps.
func Insert(ctx context.Context, q db.Querier, r *RecurringOffDay, now time.Time) error {
	r.CreatedAt, r.UpdatedAt = now, now
	return q.QueryRow(ctx, `INSERT INTO recurring_off_days (user_id,recurrence_pattern,weekday,week_interval,
 reference_date,week_of_month,start_date,end_date,is_active,description,created_at,updated_at)
 VALUES($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$11) RETURNING id`, r.UserID, r.Pattern, r.Weekday,
		r.WeekInterval, r.ReferenceDate, r.WeekOfMonth, r.StartDate, r.EndDate, r.IsActive, r.Description,
		now).Scan(&r.ID)
}

// Update saves all mutable columns and refreshes updated_at.
func Update(ctx context.Context, q db.Querier, r *RecurringOffDay, now time.Time) error {
	r.UpdatedAt = now
	_, err := q.Exec(ctx, `UPDATE recurring_off_days SET recurrence_pattern=$2,weekday=$3,week_interval=$4,
 reference_date=$5,week_of_month=$6,start_date=$7,end_date=$8,is_active=$9,description=$10,updated_at=$11
 WHERE id=$1`, r.ID, r.Pattern, r.Weekday, r.WeekInterval, r.ReferenceDate, r.WeekOfMonth, r.StartDate,
		r.EndDate, r.IsActive, r.Description, now)
	return err
}

// Delete removes the rule; exemptions and warnings cascade in the database.
func Delete(ctx context.Context, q db.Querier, id int64) error {
	_, err := q.Exec(ctx, `DELETE FROM recurring_off_days WHERE id=$1`, id)
	return err
}

const exemptionColumns = `id,recurring_off_day_id,exemption_date,reason,created_at`

func scanExemption(row pgx.Row) (*Exemption, error) {
	var e Exemption
	err := row.Scan(&e.ID, &e.RecurringOffDayID, &e.Date, &e.Reason, &e.CreatedAt)
	return &e, err
}

// FindExemptionByID returns nil when the exemption does not exist.
func FindExemptionByID(ctx context.Context, q db.Querier, id int64) (*Exemption, error) {
	e, err := scanExemption(q.QueryRow(ctx, `SELECT `+exemptionColumns+
		` FROM recurring_off_day_exemptions WHERE id=$1`, id))
	if errors.Is(err, pgx.ErrNoRows) {
		return nil, nil
	}
	return e, err
}

// FindExemptions is findByRecurringOffDayIdOrderByExemptionDateDesc.
func FindExemptions(ctx context.Context, q db.Querier, rodID int64) ([]Exemption, error) {
	rows, err := q.Query(ctx, `SELECT `+exemptionColumns+` FROM recurring_off_day_exemptions
 WHERE recurring_off_day_id=$1 ORDER BY exemption_date DESC, id`, rodID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	out := []Exemption{}
	for rows.Next() {
		e, err := scanExemption(rows)
		if err != nil {
			return nil, err
		}
		out = append(out, *e)
	}
	return out, rows.Err()
}

// ExemptionExists is existsByRecurringOffDayIdAndExemptionDate.
func ExemptionExists(ctx context.Context, q db.Querier, rodID int64, date time.Time) (bool, error) {
	var ok bool
	err := q.QueryRow(ctx, `SELECT EXISTS(SELECT 1 FROM recurring_off_day_exemptions
 WHERE recurring_off_day_id=$1 AND exemption_date=$2)`, rodID, date).Scan(&ok)
	return ok, err
}

// InsertExemption saves a new exemption and fills ID and createdAt.
func InsertExemption(ctx context.Context, q db.Querier, e *Exemption, now time.Time) error {
	e.CreatedAt = now
	return q.QueryRow(ctx, `INSERT INTO recurring_off_day_exemptions
 (recurring_off_day_id,exemption_date,reason,created_at) VALUES($1,$2,$3,$4) RETURNING id`,
		e.RecurringOffDayID, e.Date, e.Reason, now).Scan(&e.ID)
}

// DeleteExemption removes one exemption.
func DeleteExemption(ctx context.Context, q db.Querier, id int64) error {
	_, err := q.Exec(ctx, `DELETE FROM recurring_off_day_exemptions WHERE id=$1`, id)
	return err
}

// DeleteExemptionsByRule is deleteByRecurringOffDayId.
func DeleteExemptionsByRule(ctx context.Context, q db.Querier, rodID int64) error {
	_, err := q.Exec(ctx, `DELETE FROM recurring_off_day_exemptions WHERE recurring_off_day_id=$1`, rodID)
	return err
}
