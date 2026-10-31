package vacationbalance

import (
	"context"
	"fmt"
	"time"

	"github.com/steffbo/timetrack/backend/internal/db"
	"github.com/steffbo/timetrack/backend/internal/domain"
	"github.com/steffbo/timetrack/backend/internal/httpx"
	"github.com/steffbo/timetrack/backend/internal/leavestore"
	"github.com/steffbo/timetrack/backend/internal/recurringoffdays"
	"github.com/steffbo/timetrack/backend/internal/users"
	"github.com/steffbo/timetrack/backend/internal/workinghours"
)

// WorkingDays ports WorkingDaysCalculator.calculateWorkingDays for one user. The result is a multiple
// of 0.5, which is exactly representable as float64.
// excludeID is the time-off entry to ignore (0 for none).
func WorkingDays(
	ctx context.Context, q db.Querier, userID int64, state domain.GermanState,
	start, end time.Time, excludeID int64,
) (float64, error) {
	if start.After(end) {
		return 0, nil
	}
	user, err := userForCalculation(ctx, q, userID)
	if err != nil {
		return 0, err
	}
	in := domain.WorkingDayInput{WorkingWeekdays: map[time.Weekday]bool{},
		Holidays: map[string]bool{}, RecurringOff: map[string]bool{},
		HalfDaysEnabled: user.HalfDayHolidaysEnabled}
	days, err := (workinghours.Repository{Q: q}).FindByUserID(ctx, userID)
	if err != nil {
		return 0, err
	}
	for _, day := range days {
		in.WorkingWeekdays[time.Weekday(day.Weekday%7)] = day.IsWorkingDay
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
	entries, err := leavestore.FindByUserIDAndDateRange(ctx, q, userID, start, end)
	if err != nil {
		return 0, err
	}
	var others []leavestore.TimeOff
	for _, t := range entries {
		if t.Type != domain.Vacation && (excludeID == 0 || t.ID != excludeID) {
			others = append(others, t)
		}
	}
	// Sick/personal entries take precedence over recurring off-days, so they skip that check.
	checkRecurring := true
	if excludeID != 0 {
		excluded, err := leavestore.FindByID(ctx, q, excludeID)
		if err != nil {
			return 0, err
		}
		if excluded != nil && excluded.Type != domain.Vacation {
			checkRecurring = false
		}
	}
	for _, t := range others {
		in.OtherTimeOff = append(in.OtherTimeOff, domain.DateRange{Start: t.StartDate, End: t.EndDate})
	}
	for day := start; !day.After(end); day = day.AddDate(0, 0, 1) {
		key := day.Format(time.DateOnly)
		in.Holidays[key] = (domain.GermanPublicHolidays{}).IsPublicHoliday(day, state)
		if checkRecurring {
			for i := range rods {
				if recurringoffdays.AppliesToDateWithExemptions(&rods[i], day, exempted) {
					in.RecurringOff[key] = true
					break
				}
			}
		}
	}
	return domain.WorkingDays(start, end, in), nil
}

func userNotFoundMessage(id int64) string { return fmt.Sprintf("User not found with ID: %d", id) }

func userForCalculation(ctx context.Context, q db.Querier, id int64) (users.User, error) {
	user, found, err := (users.Repository{Q: q}).FindByID(ctx, id)
	if err != nil {
		return user, err
	}
	if !found {
		return user, httpx.InvalidArgument(userNotFoundMessage(id))
	}
	return user, nil
}
