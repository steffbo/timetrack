package timeoff

import (
	"context"
	"errors"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/steffbo/timetrack/backend-go/internal/clock"
	"github.com/steffbo/timetrack/backend-go/internal/db"
	"github.com/steffbo/timetrack/backend-go/internal/domain"
)

// defaultAnnualAllowanceDays is VacationBalanceService.DEFAULT_ANNUAL_ALLOWANCE_DAYS.
const defaultAnnualAllowanceDays = 30.0

func utcDate(y int, m time.Month, d int) time.Time { return time.Date(y, m, d, 0, 0, 0, 0, time.UTC) }

// RecalculateVacationBalanceForDateRange ports VacationBalanceService.recalculateVacationBalanceForDateRange.
// now is the application clock reading; q must be a transaction for atomicity.
func RecalculateVacationBalanceForDateRange(
	ctx context.Context, q db.Querier, userID int64, start, end, now time.Time,
) error {
	for year := start.Year(); year <= end.Year(); year++ {
		if err := recalculateVacationBalance(ctx, q, userID, year, now); err != nil {
			return err
		}
	}
	return nil
}

func recalculateVacationBalance(ctx context.Context, q db.Querier, userID int64, year int, now time.Time) error {
	today := utcDate(now.Year(), now.Month(), now.Day())
	entries, err := FindByUserIDAndTypeAndYear(ctx, q, userID, domain.Vacation,
		utcDate(year, time.January, 1), utcDate(year, time.December, 31))
	if err != nil {
		return err
	}
	var state domain.GermanState
	if err = q.QueryRow(ctx, `SELECT state FROM users WHERE id=$1`, userID).Scan(&state); err != nil {
		return notFoundAsIllegalArgument(err, userID)
	}
	used := 0.0
	for _, t := range entries {
		if t.EndDate.After(today) { // only past vacation counts as used
			continue
		}
		days, err := WorkingDays(ctx, q, userID, state, t.StartDate, t.EndDate, 0)
		if err != nil {
			return err
		}
		used += days
	}
	ts := clock.LocalTimestamp(now)
	var id int64
	err = q.QueryRow(ctx, `SELECT id FROM vacation_balance WHERE user_id=$1 AND year=$2`, userID, year).Scan(&id)
	if errors.Is(err, pgx.ErrNoRows) {
		_, err = q.Exec(ctx, `INSERT INTO vacation_balance (user_id,year,annual_allowance_days,carried_over_days,
 adjustment_days,used_days,remaining_days,created_at,updated_at)
 VALUES($1,$2,$3::float8::numeric,0,0,$4::float8::numeric,($3::float8-$4::float8)::numeric,$5,$5)`,
			userID, year, defaultAnnualAllowanceDays, used, ts)
		return err
	}
	if err != nil {
		return err
	}
	// Like Hibernate, only touch the row (and updated_at) when a value changes.
	_, err = q.Exec(ctx, `UPDATE vacation_balance SET used_days=u.used, remaining_days=u.remaining, updated_at=$3
 FROM (SELECT round($2::float8::numeric,1) AS used, round(annual_allowance_days+COALESCE(carried_over_days,0)
  +COALESCE(adjustment_days,0)-$2::float8::numeric,1) AS remaining FROM vacation_balance WHERE id=$1) u
 WHERE id=$1 AND (vacation_balance.used_days IS DISTINCT FROM u.used
  OR vacation_balance.remaining_days IS DISTINCT FROM u.remaining)`, id, used, ts)
	return err
}
