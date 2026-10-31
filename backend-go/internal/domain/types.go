// Package domain owns shared enums and policies; keep additions backward compatible.
package domain

type Role string

const (
	Admin Role = "ADMIN"
	User  Role = "USER"
)

type GermanState string

const (
	Berlin      GermanState = "BERLIN"
	Brandenburg GermanState = "BRANDENBURG"
)

type TimeOffType string

const (
	Vacation      TimeOffType = "VACATION"
	Sick          TimeOffType = "SICK"
	ChildSick     TimeOffType = "CHILD_SICK"
	Personal      TimeOffType = "PERSONAL"
	PublicHoliday TimeOffType = "PUBLIC_HOLIDAY"
	Education     TimeOffType = "EDUCATION"
)

type EntryType string

const Work EntryType = "WORK"

type RecurrencePattern string

const (
	EveryNthWeek      RecurrencePattern = "EVERY_NTH_WEEK"
	NthWeekdayOfMonth RecurrencePattern = "NTH_WEEKDAY_OF_MONTH"
)

type DayTypePrecedence string

const (
	DayWeekend         DayTypePrecedence = "WEEKEND"
	DayPublicHoliday   DayTypePrecedence = "PUBLIC_HOLIDAY"
	DaySick            DayTypePrecedence = "SICK"
	DayPersonal        DayTypePrecedence = "PERSONAL"
	DayEducation       DayTypePrecedence = "EDUCATION"
	DayRecurringOffDay DayTypePrecedence = "RECURRING_OFF_DAY"
	DayVacation        DayTypePrecedence = "VACATION"
	DayWork            DayTypePrecedence = "WORK"
	DayNoEntry         DayTypePrecedence = "NO_ENTRY"
)

func (d DayTypePrecedence) Priority() int {
	return map[DayTypePrecedence]int{DayWeekend: 1, DayPublicHoliday: 2, DaySick: 3, DayPersonal: 4,
		DayEducation: 4, DayRecurringOffDay: 5, DayVacation: 6, DayWork: 7, DayNoEntry: 8}[d]
}
func (d DayTypePrecedence) HasHigherPriorityThan(other DayTypePrecedence) bool {
	return d.Priority() < other.Priority()
}
