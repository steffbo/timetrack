package recurringoffdays

import (
	"context"
	"time"

	"github.com/steffbo/timetrack/backend-go/internal/db"
	"github.com/steffbo/timetrack/backend-go/internal/domain"
)

func isoWeekday(t time.Time) int {
	if w := int(t.Weekday()); w != 0 {
		return w
	}
	return 7
}

// weeksBetween is ChronoUnit.WEEKS.between for dates: whole days divided by seven, truncated to zero.
func weeksBetween(from, to time.Time) int {
	return int((to.Unix() - from.Unix()) / 86400 / 7)
}

// AppliesToDate ports RecurringOffDayEvaluator.appliesToDate (ignores exemptions).
func AppliesToDate(r *RecurringOffDay, date time.Time) bool {
	if !r.IsActive || date.Before(r.StartDate) {
		return false
	}
	if r.EndDate != nil && date.After(*r.EndDate) {
		return false
	}
	if isoWeekday(date) != r.Weekday {
		return false
	}
	switch r.Pattern {
	case domain.EveryNthWeek:
		if r.ReferenceDate == nil || r.WeekInterval == nil || *r.WeekInterval < 1 {
			return false
		}
		weeks := weeksBetween(*r.ReferenceDate, date)
		return weeks >= 0 && weeks%*r.WeekInterval == 0
	case domain.NthWeekdayOfMonth:
		if r.WeekOfMonth == nil || *r.WeekOfMonth < 1 || *r.WeekOfMonth > 5 {
			return false
		}
		if *r.WeekOfMonth == 5 { // last occurrence of the weekday in the month
			return date.AddDate(0, 0, 7).Month() != date.Month()
		}
		return (date.Day()-1)/7+1 == *r.WeekOfMonth
	}
	return false
}

// ExemptionLookup answers whether a rule is exempted on a date.
type ExemptionLookup func(ruleID int64, date time.Time) bool

// AppliesToDateWithExemptions ports appliesToDateWithExemptions with a preloaded lookup.
func AppliesToDateWithExemptions(r *RecurringOffDay, date time.Time, exempted ExemptionLookup) bool {
	if !AppliesToDate(r, date) {
		return false
	}
	return r.ID == 0 || !exempted(r.ID, date)
}

// Evaluator queries exemptions on demand, like the Spring bean.
type Evaluator struct{ Q db.Querier }

// IsExempted ports isExempted.
func (e Evaluator) IsExempted(ctx context.Context, r *RecurringOffDay, date time.Time) (bool, error) {
	if r.ID == 0 {
		return false, nil
	}
	return ExemptionExists(ctx, e.Q, r.ID, date)
}

// AppliesToDateWithExemptions ports the method of the same name.
func (e Evaluator) AppliesToDateWithExemptions(
	ctx context.Context, r *RecurringOffDay, date time.Time,
) (bool, error) {
	if !AppliesToDate(r, date) {
		return false, nil
	}
	exempted, err := e.IsExempted(ctx, r, date)
	return !exempted, err
}

// LoadExemptions reads all exemption dates of the given rules into a lookup.
func LoadExemptions(ctx context.Context, q db.Querier, ruleIDs []int64) (ExemptionLookup, error) {
	type key struct {
		id   int64
		date string
	}
	set := map[key]bool{}
	if len(ruleIDs) > 0 {
		rows, err := q.Query(ctx, `SELECT recurring_off_day_id,exemption_date
 FROM recurring_off_day_exemptions WHERE recurring_off_day_id=ANY($1)`, ruleIDs)
		if err != nil {
			return nil, err
		}
		defer rows.Close()
		for rows.Next() {
			var id int64
			var d time.Time
			if err = rows.Scan(&id, &d); err != nil {
				return nil, err
			}
			set[key{id, d.Format(time.DateOnly)}] = true
		}
		if err = rows.Err(); err != nil {
			return nil, err
		}
	}
	return func(id int64, date time.Time) bool { return set[key{id, date.Format(time.DateOnly)}] }, nil
}
