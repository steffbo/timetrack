package domain

import "time"

// RecurringRule is the shared, persistence-independent evaluator input.
// Weekday follows Java DayOfWeek (Monday=1, Sunday=7).
type RecurringRule struct {
	ID            int64
	Active        bool
	Pattern       RecurrencePattern
	Weekday       int
	StartDate     time.Time
	EndDate       *time.Time
	ReferenceDate *time.Time
	WeekInterval  *int
	WeekOfMonth   *int
}

func dateOnly(t time.Time) time.Time {
	return time.Date(t.Year(), t.Month(), t.Day(), 0, 0, 0, 0, time.UTC)
}

// AppliesToDate mirrors RecurringOffDayEvaluator, including truncated whole weeks
// before the reference date and weekOfMonth=5 meaning the last occurrence.
func (r RecurringRule) AppliesToDate(date time.Time) bool {
	date = dateOnly(date)
	if !r.Active || date.Before(dateOnly(r.StartDate)) ||
		(r.EndDate != nil && date.After(dateOnly(*r.EndDate))) ||
		(int(date.Weekday())+6)%7+1 != r.Weekday {
		return false
	}
	switch r.Pattern {
	case EveryNthWeek:
		if r.ReferenceDate == nil || r.WeekInterval == nil || *r.WeekInterval < 1 {
			return false
		}
		weeks := int(date.Sub(dateOnly(*r.ReferenceDate)).Hours()/24) / 7
		return weeks >= 0 && weeks%*r.WeekInterval == 0
	case NthWeekdayOfMonth:
		if r.WeekOfMonth == nil || *r.WeekOfMonth < 1 || *r.WeekOfMonth > 5 {
			return false
		}
		if *r.WeekOfMonth == 5 {
			return date.AddDate(0, 0, 7).Month() != date.Month()
		}
		return (date.Day()-1)/7+1 == *r.WeekOfMonth
	}
	return false
}

func (r RecurringRule) AppliesToDateWithExemptions(date time.Time, exempted bool) bool {
	return r.AppliesToDate(date) && !exempted
}
