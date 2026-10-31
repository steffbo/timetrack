package timeentries

import (
	"context"
	"time"

	"github.com/jackc/pgx/v5"

	"github.com/steffbo/timetrack/backend/internal/api"
	"github.com/steffbo/timetrack/backend/internal/conflictwarnings"
	"github.com/steffbo/timetrack/backend/internal/db"
	"github.com/steffbo/timetrack/backend/internal/domain"
	"github.com/steffbo/timetrack/backend/internal/leavestore"
	"github.com/steffbo/timetrack/backend/internal/recurringoffdays"
	"github.com/steffbo/timetrack/backend/internal/timeoff"
	"github.com/steffbo/timetrack/backend/internal/users"
	"github.com/steffbo/timetrack/backend/internal/workinghours"
)

type WorkingDay struct {
	Working bool
	Hours   float64
}
type Rule struct {
	Response   api.RecurringOffDayResponse
	Pattern    recurringoffdays.RecurringOffDay
	Exemptions recurringoffdays.ExemptionLookup
}

func (r Rule) Applies(date time.Time) bool {
	return recurringoffdays.AppliesToDateWithExemptions(&r.Pattern, date, r.Exemptions)
}

// CalendarData composes the owning repositories for summaries and reports.
type CalendarData struct {
	Working  map[int]WorkingDay
	TimeOff  []api.TimeOffResponse
	Rules    []Rule
	State    domain.GermanState
	HalfDays bool
}

func (c CalendarData) Expected(date time.Time) float64 {
	day := c.Working[(int(date.Weekday())+6)%7+1]
	if day.Working {
		return day.Hours
	}
	return 0
}
func (c CalendarData) Recurring(date time.Time) bool {
	for _, r := range c.Rules {
		if r.Applies(date) {
			return true
		}
	}
	return false
}
func Covers(o api.TimeOffResponse, date time.Time) bool {
	return !date.Before(o.StartDate.Time) && !date.After(o.EndDate.Time)
}

// LoadCalendar performs only reads of the other domains' tables. Time off is loaded
// for all dates so the response days count covers an entire multi-month absence.
func LoadCalendar(ctx context.Context, q db.Querier, uid int64) (CalendarData, error) {
	c := CalendarData{Working: map[int]WorkingDay{}, TimeOff: []api.TimeOffResponse{}, Rules: []Rule{}}
	user, found, err := (users.Repository{Q: q}).FindByID(ctx, uid)
	if err != nil {
		return c, err
	}
	if !found {
		return c, pgx.ErrNoRows
	}
	c.State, c.HalfDays = user.State, user.HalfDayHolidaysEnabled
	days, err := (workinghours.Repository{Q: q}).FindByUserID(ctx, uid)
	if err != nil {
		return c, err
	}
	for _, day := range days {
		c.Working[day.Weekday] = WorkingDay{Working: day.IsWorkingDay, Hours: day.Hours}
	}
	rules, err := recurringoffdays.FindByUserID(ctx, q, uid)
	if err != nil {
		return c, err
	}
	ids := make([]int64, len(rules))
	for i := range rules {
		ids[i] = rules[i].ID
	}
	exemptions, err := recurringoffdays.LoadExemptions(ctx, q, ids)
	if err != nil {
		return c, err
	}
	for i := range rules {
		c.Rules = append(c.Rules, Rule{Response: recurringoffdays.ToResponse(&rules[i]),
			Pattern: rules[i], Exemptions: exemptions})
	}
	entries, err := leavestore.FindByUserID(ctx, q, uid)
	if err != nil {
		return c, err
	}
	for i := range entries {
		response, err := timeoff.ToResponse(ctx, q, &entries[i])
		if err != nil {
			return c, err
		}
		c.TimeOff = append(c.TimeOff, response)
	}
	return c, nil
}

func warnings(ctx context.Context, q db.Querier, uid int64, start, end time.Time) (
	map[string]*api.RecurringOffDayConflictWarningResponse, error) {
	entries, err := conflictwarnings.FindByUserIDAndDateRange(ctx, q, uid, start, end)
	if err != nil {
		return nil, err
	}
	out := map[string]*api.RecurringOffDayConflictWarningResponse{}
	for i := range entries {
		response, err := conflictwarnings.ToResponse(ctx, q, &entries[i])
		if err != nil {
			return nil, err
		}
		out[response.ConflictDate.String()] = &response
	}
	return out, nil
}
