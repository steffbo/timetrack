package vacationbalance

import (
	"context"
	"time"

	"github.com/steffbo/timetrack/backend/internal/db"
	"github.com/steffbo/timetrack/backend/internal/domain"
	"github.com/steffbo/timetrack/backend/internal/leavestore"
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

func recalculateVacationBalance(
	ctx context.Context, q db.Querier, userID int64, year int, now time.Time,
) error {
	b, err := findOrCreate(ctx, q, userID, year, now)
	if err != nil {
		return err
	}
	used, _, err := totals(ctx, q, userID, year, now)
	if err != nil {
		return err
	}
	b.Used = used
	return b.save(ctx, q, now)
}

func totals(
	ctx context.Context,
	q db.Querier,
	uid int64,
	year int,
	now time.Time,
) (float64, float64, error) {
	entries, err := leavestore.FindByUserIDAndTypeAndYear(ctx, q, uid, domain.Vacation,
		utcDate(year, time.January, 1), utcDate(year, time.December, 31))
	if err != nil {
		return 0, 0, err
	}
	user, err := userForCalculation(ctx, q, uid)
	if err != nil {
		return 0, 0, err
	}
	var used, planned float64
	today := utcDate(now.Year(), now.Month(), now.Day())
	for _, entry := range entries {
		days, err := WorkingDays(ctx, q, uid, user.State, entry.StartDate, entry.EndDate, 0)
		if err != nil {
			return 0, 0, err
		}
		planned += days
		if !entry.EndDate.After(today) {
			used += days
		}
	}
	return used, planned, nil
}
