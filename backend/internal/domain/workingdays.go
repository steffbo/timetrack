package domain

import "time"

// WorkingDayInput contains the date-level facts needed to calculate leave days.
type WorkingDayInput struct {
	WorkingWeekdays map[time.Weekday]bool
	Holidays        map[string]bool
	OtherTimeOff    []DateRange
	RecurringOff    map[string]bool
	HalfDaysEnabled bool
}

type DateRange struct {
	Start time.Time
	End   time.Time
}

// WorkingDays counts configured working days inclusively, applying
// precedence: schedule, public holiday, other leave, recurring off day.
func WorkingDays(start, end time.Time, in WorkingDayInput) float64 {
	if start.After(end) {
		return 0
	}
	var total float64
	for date := start; !date.After(end); date = date.AddDate(0, 0, 1) {
		key := date.Format(time.DateOnly)
		if !in.WorkingWeekdays[date.Weekday()] || in.Holidays[key] {
			continue
		}
		if coveredByRange(date, in.OtherTimeOff) || in.RecurringOff[key] {
			continue
		}
		if in.HalfDaysEnabled && date.Month() == time.December &&
			(date.Day() == 24 || date.Day() == 31) {
			total += 0.5
		} else {
			total++
		}
	}
	return total
}

func coveredByRange(date time.Time, ranges []DateRange) bool {
	for _, dateRange := range ranges {
		if !date.Before(dateRange.Start) && !date.After(dateRange.End) {
			return true
		}
	}
	return false
}
