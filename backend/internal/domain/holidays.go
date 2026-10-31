package domain

import "time"

type GermanPublicHolidays struct{}

func EasterSunday(year int) time.Time {
	a := year % 19
	b := year / 100
	c := year % 100
	d := b / 4
	e := b % 4
	f := (b + 8) / 25
	g := (b - f + 1) / 3
	h := (19*a + b - d - g + 15) % 30
	i := c / 4
	k := c % 4
	l := (32 + 2*e + 2*i - h - k) % 7
	m := (a + 11*h + 22*l) / 451
	return time.Date(year, time.Month((h+l-7*m+114)/31), (h+l-7*m+114)%31+1, 0, 0, 0, 0, time.UTC)
}
func (GermanPublicHolidays) GetPublicHolidays(year int, state GermanState) []time.Time {
	date := func(m time.Month, d int) time.Time { return time.Date(year, m, d, 0, 0, 0, 0, time.UTC) }
	easter := EasterSunday(year)
	out := []time.Time{date(1, 1), date(5, 1), date(10, 3), date(12, 25), date(12, 26),
		easter.AddDate(0, 0, -2), easter.AddDate(0, 0, 1), easter.AddDate(0, 0, 39), easter.AddDate(0, 0, 50)}
	switch state {
	case Berlin:
		out = append(out, date(3, 8))
	case Brandenburg:
		out = append(out, date(10, 31))
	}
	return out
}
func (h GermanPublicHolidays) IsPublicHoliday(date time.Time, state GermanState) bool {
	for _, day := range h.GetPublicHolidays(date.Year(), state) {
		if date.Format(time.DateOnly) == day.Format(time.DateOnly) {
			return true
		}
	}
	return false
}
