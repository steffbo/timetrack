package timeoff

import (
	"context"
	"errors"
	"fmt"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/steffbo/timetrack/backend-go/internal/db"
	"github.com/steffbo/timetrack/backend-go/internal/domain"
	"github.com/steffbo/timetrack/backend-go/internal/httpx"
	"github.com/steffbo/timetrack/backend-go/internal/recurringoffdays"
)

func isoWeekday(t time.Time) int {
	if w := int(t.Weekday()); w != 0 {
		return w
	}
	return 7
}

// WorkingDays ports WorkingDaysCalculator.calculateWorkingDays for one user. The result is a multiple
// of 0.5, so it is computed in half days and halved at the end (BigDecimal 0.5 steps are exact).
// excludeID is the time-off entry to ignore (0 for none).
func WorkingDays(
	ctx context.Context, q db.Querier, userID int64, state domain.GermanState, start, end time.Time, excludeID int64,
) (float64, error) {
	if start.After(end) {
		return 0, nil
	}
	var halfDayEnabled bool
	if err := q.QueryRow(ctx, `SELECT half_day_holidays_enabled FROM users WHERE id=$1`, userID).
		Scan(&halfDayEnabled); err != nil {
		return 0, notFoundAsIllegalArgument(err, userID)
	}
	working := map[int]bool{}
	rows, err := q.Query(ctx, `SELECT weekday,is_working_day FROM working_hours WHERE user_id=$1 ORDER BY id`, userID)
	if err != nil {
		return 0, err
	}
	for rows.Next() {
		var weekday int
		var isWorking bool
		if err = rows.Scan(&weekday, &isWorking); err != nil {
			rows.Close()
			return 0, err
		}
		if _, seen := working[weekday]; !seen { // findFirst
			working[weekday] = isWorking
		}
	}
	rows.Close()
	if err = rows.Err(); err != nil {
		return 0, err
	}
	rods, err := recurringoffdays.FindByUserID(ctx, q, userID)
	if err != nil {
		return 0, err
	}
	ruleIDs := make([]int64, len(rods))
	for i := range rods {
		ruleIDs[i] = rods[i].ID
	}
	exempted, err := recurringoffdays.LoadExemptions(ctx, q, ruleIDs)
	if err != nil {
		return 0, err
	}
	entries, err := FindByUserIDAndDateRange(ctx, q, userID, start, end)
	if err != nil {
		return 0, err
	}
	var others []TimeOff
	for _, t := range entries {
		if t.Type != domain.Vacation && (excludeID == 0 || t.ID != excludeID) {
			others = append(others, t)
		}
	}
	// Sick/personal entries take precedence over recurring off-days, so they skip that check.
	checkRecurring := true
	if excludeID != 0 {
		excluded, err := FindByID(ctx, q, excludeID)
		if err != nil {
			return 0, err
		}
		if excluded != nil && excluded.Type != domain.Vacation {
			checkRecurring = false
		}
	}
	holidays := domain.GermanPublicHolidays{}
	halves := 0
	for day := start; !day.After(end); day = day.AddDate(0, 0, 1) {
		if !working[isoWeekday(day)] || holidays.IsPublicHoliday(day, state) {
			continue
		}
		covered := false
		for _, t := range others {
			if !day.Before(t.StartDate) && !day.After(t.EndDate) {
				covered = true
				break
			}
		}
		if covered {
			continue
		}
		if checkRecurring {
			off := false
			for i := range rods {
				if recurringoffdays.AppliesToDateWithExemptions(&rods[i], day, exempted) {
					off = true
					break
				}
			}
			if off {
				continue
			}
		}
		if halfDayEnabled && day.Month() == time.December && (day.Day() == 24 || day.Day() == 31) {
			halves++
		} else {
			halves += 2
		}
	}
	return float64(halves) / 2, nil
}

func notFoundAsIllegalArgument(err error, userID int64) error {
	if errors.Is(err, pgx.ErrNoRows) {
		return httpx.InvalidArgument(fmt.Sprintf("User not found with ID: %d", userID))
	}
	return err
}
