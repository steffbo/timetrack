package timeentries

import (
	"context"
	"time"

	openapi_types "github.com/oapi-codegen/runtime/types"
	"github.com/steffbo/timetrack/backend-go/internal/api"
	"github.com/steffbo/timetrack/backend-go/internal/db"
	"github.com/steffbo/timetrack/backend-go/internal/domain"
)

type WorkingDay struct {
	Working bool
	Hours   float64
}
type Rule struct {
	Response   api.RecurringOffDayResponse
	Pattern    domain.RecurringRule
	Exemptions map[string]bool
}

func (r Rule) Applies(date time.Time) bool {
	return r.Pattern.AppliesToDateWithExemptions(date, r.Exemptions[date.Format(time.DateOnly)])
}

// CalendarData contains the private read-model used by package A's summaries and reports.
// No dependency on another porting package's repositories is required.
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
func (c CalendarData) WorkingDays(o api.TimeOffResponse) float64 {
	var days float64
	for d := o.StartDate.Time; !d.After(o.EndDate.Time); d = d.AddDate(0, 0, 1) {
		wh := c.Working[(int(d.Weekday())+6)%7+1]
		if !wh.Working || (domain.GermanPublicHolidays{}).IsPublicHoliday(d, c.State) {
			continue
		}
		excluded := false
		for _, other := range c.TimeOff {
			if other.Id != o.Id && other.TimeOffType != "VACATION" && Covers(other, d) {
				excluded = true
				break
			}
		}
		if excluded || (o.TimeOffType == "VACATION" && c.Recurring(d)) {
			continue
		}
		if c.HalfDays && d.Month() == 12 && (d.Day() == 24 || d.Day() == 31) {
			days += 0.5
		} else {
			days++
		}
	}
	return days
}

// LoadCalendar performs only reads of the other domains' tables. Time off is loaded
// for all dates so the response days count covers an entire multi-month absence.
func LoadCalendar(ctx context.Context, q db.Querier, uid int64) (CalendarData, error) {
	c := CalendarData{Working: map[int]WorkingDay{}, TimeOff: []api.TimeOffResponse{}, Rules: []Rule{}}
	err := q.QueryRow(ctx, `SELECT state,half_day_holidays_enabled FROM users WHERE id=$1`, uid).
		Scan(&c.State, &c.HalfDays)
	if err != nil {
		return c, err
	}
	rows, err := q.Query(ctx, `SELECT weekday,is_working_day,COALESCE(hours,0)::float8
 FROM working_hours WHERE user_id=$1`, uid)
	if err != nil {
		return c, err
	}
	for rows.Next() {
		var day int
		var wh WorkingDay
		if err = rows.Scan(&day, &wh.Working, &wh.Hours); err != nil {
			rows.Close()
			return c, err
		}
		c.Working[day] = wh
	}
	err = rows.Err()
	rows.Close()
	if err != nil {
		return c, err
	}
	rows, err = q.Query(ctx, `SELECT id,user_id,recurrence_pattern,weekday,week_interval,reference_date,
 week_of_month,start_date,end_date,is_active,description,created_at,updated_at
 FROM recurring_off_days WHERE user_id=$1`, uid)
	if err != nil {
		return c, err
	}
	for rows.Next() {
		var r Rule
		var ref, end *time.Time
		v := &r.Response
		if err = rows.Scan(&v.Id, &v.UserId, &v.RecurrencePattern, &v.Weekday, &v.WeekInterval, &ref,
			&v.WeekOfMonth, &v.StartDate.Time, &end, &v.IsActive, &v.Description,
			&v.CreatedAt, &v.UpdatedAt); err != nil {
			rows.Close()
			return c, err
		}
		if ref != nil {
			v.ReferenceDate = &openapi_types.Date{Time: *ref}
		}
		if end != nil {
			v.EndDate = &openapi_types.Date{Time: *end}
		}
		r.Pattern = domain.RecurringRule{ID: v.Id, Active: v.IsActive,
			Pattern: domain.RecurrencePattern(v.RecurrencePattern),
			Weekday: v.Weekday, StartDate: v.StartDate.Time, EndDate: end, ReferenceDate: ref,
			WeekInterval: v.WeekInterval, WeekOfMonth: v.WeekOfMonth}
		r.Exemptions = map[string]bool{}
		c.Rules = append(c.Rules, r)
	}
	err = rows.Err()
	rows.Close()
	if err != nil {
		return c, err
	}
	rows, err = q.Query(ctx, `SELECT e.recurring_off_day_id,e.exemption_date
 FROM recurring_off_day_exemptions e JOIN recurring_off_days r ON r.id=e.recurring_off_day_id
 WHERE r.user_id=$1`, uid)
	if err != nil {
		return c, err
	}
	for rows.Next() {
		var id int64
		var d time.Time
		if err = rows.Scan(&id, &d); err != nil {
			rows.Close()
			return c, err
		}
		for i := range c.Rules {
			if c.Rules[i].Response.Id == id {
				c.Rules[i].Exemptions[d.Format(time.DateOnly)] = true
			}
		}
	}
	err = rows.Err()
	rows.Close()
	if err != nil {
		return c, err
	}
	rows, err = q.Query(ctx, `SELECT id,user_id,start_date,end_date,time_off_type,hours_per_day::float8,
 notes,confirmed,created_at,updated_at FROM time_off WHERE user_id=$1`, uid)
	if err != nil {
		return c, err
	}
	for rows.Next() {
		var o api.TimeOffResponse
		if err = rows.Scan(&o.Id, &o.UserId, &o.StartDate.Time, &o.EndDate.Time, &o.TimeOffType, &o.HoursPerDay,
			&o.Notes, &o.Confirmed, &o.CreatedAt, &o.UpdatedAt); err != nil {
			rows.Close()
			return c, err
		}
		c.TimeOff = append(c.TimeOff, o)
	}
	err = rows.Err()
	rows.Close()
	return c, err
}

func warnings(ctx context.Context, q db.Querier, uid int64, start, end time.Time) (
	map[string]*api.RecurringOffDayConflictWarningResponse, error) {
	rows, err := q.Query(ctx, `SELECT w.id,w.user_id,w.conflict_date,w.time_entry_id,w.recurring_off_day_id,
 r.description,w.acknowledged,w.acknowledged_at,w.created_at,w.updated_at
 FROM recurring_off_day_conflict_warnings w LEFT JOIN recurring_off_days r ON r.id=w.recurring_off_day_id
 WHERE w.user_id=$1 AND w.conflict_date BETWEEN $2 AND $3 ORDER BY w.conflict_date DESC`, uid, start, end)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	out := map[string]*api.RecurringOffDayConflictWarningResponse{}
	for rows.Next() {
		w := new(api.RecurringOffDayConflictWarningResponse)
		if err = rows.Scan(&w.Id, &w.UserId, &w.ConflictDate.Time, &w.TimeEntryId, &w.RecurringOffDayId,
			&w.RecurringOffDayDescription, &w.Acknowledged, &w.AcknowledgedAt,
			&w.CreatedAt, &w.UpdatedAt); err != nil {
			return nil, err
		}
		out[w.ConflictDate.String()] = w
	}
	return out, rows.Err()
}
