package reports

import (
	"context"
	"errors"
	"fmt"
	"strings"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/steffbo/timetrack/backend-go/internal/api"
	"github.com/steffbo/timetrack/backend-go/internal/httpx"
	"github.com/steffbo/timetrack/backend-go/internal/timeentries"
)

type DailyReportEntry struct {
	Date                                time.Time
	StartTime, EndTime                  *time.Time
	BreakMinutes                        int
	TotalHours, ExpectedHours, Overtime *float64
	DayType                             string
	Notes                               *string
	TimeOffType                         string
}
type User struct{ FirstName, LastName *string }
type Service struct{ Pool *pgxpool.Pool }

func validate(year, month int) error {
	if year < 2000 || year > 2100 {
		return httpx.InvalidArgument("Year must be between 2000 and 2100")
	}
	if month < 1 || month > 12 {
		return httpx.InvalidArgument("Month must be between 1 and 12")
	}
	return nil
}
func (s Service) Entries(ctx context.Context, uid int64, year, month int) ([]DailyReportEntry, error) {
	if err := validate(year, month); err != nil {
		return nil, err
	}
	start := time.Date(year, time.Month(month), 1, 0, 0, 0, 0, time.UTC)
	end := start.AddDate(0, 1, -1)
	entries, err := (timeentries.Repository{DB: s.Pool}).List(ctx, uid, &start, &end)
	if err != nil {
		return nil, err
	}
	calendar, err := timeentries.LoadCalendar(ctx, s.Pool, uid)
	if err != nil {
		return nil, err
	}
	berlin, err := time.LoadLocation("Europe/Berlin")
	if err != nil {
		return nil, err
	}
	out := []DailyReportEntry{}
	for d := start; !d.After(end); d = d.AddDate(0, 0, 1) {
		day := []timeentries.Entry{}
		for _, e := range entries {
			if e.EntryDate.Equal(d) {
				day = append(day, e)
			}
		}
		var off *api.TimeOffResponse
		for _, o := range calendar.TimeOff {
			if timeentries.Covers(o, d) {
				if off == nil || ((o.TimeOffType == "SICK" || o.TimeOffType == "CHILD_SICK") &&
					off.TimeOffType != "SICK" && off.TimeOffType != "CHILD_SICK") {
					v := o
					off = &v
				}
			}
		}
		expected := calendar.Expected(d)
		r := DailyReportEntry{Date: d, ExpectedHours: &expected, DayType: "REGULAR"}
		if off != nil {
			r.Notes = off.Notes
			r.TimeOffType = string(off.TimeOffType)
			switch off.TimeOffType {
			case "SICK", "CHILD_SICK":
				r.DayType = "SICK"
			case "VACATION":
				r.DayType = "VACATION"
			case "PUBLIC_HOLIDAY":
				r.DayType = "PUBLIC_HOLIDAY"
			}
		} else if calendar.Recurring(d) {
			r.DayType = "RECURRING_OFF_DAY"
		} else if d.Weekday() == time.Saturday || d.Weekday() == time.Sunday {
			r.DayType = "WEEKEND"
		}
		if len(day) > 0 {
			first := day[0].ClockIn
			var last *time.Time
			active := false
			total := 0.0
			for _, e := range day {
				if e.ClockIn.Before(first) {
					first = e.ClockIn
				}
				r.BreakMinutes += e.BreakMinutes
				if e.ClockOut == nil {
					active = true
				} else if last == nil || e.ClockOut.After(*last) {
					v := *e.ClockOut
					last = &v
				}
				if h := e.HoursWorked(); h != nil {
					total += *h
				}
			}
			if r.Notes == nil {
				for _, e := range day {
					if e.Notes != nil && strings.TrimSpace(*e.Notes) != "" {
						r.Notes = e.Notes
						break
					}
				}
			}
			first = first.In(berlin)
			r.StartTime = &first
			if !active {
				if last != nil {
					v := last.In(berlin)
					r.EndTime = &v
				}
				r.TotalHours = &total
				ot := total - expected
				r.Overtime = &ot
			}
		}
		out = append(out, r)
	}
	return out, nil
}
func (s Service) PDF(ctx context.Context, uid int64, year, month int) ([]byte, error) {
	if err := validate(year, month); err != nil {
		return nil, err
	}
	var user User
	err := s.Pool.QueryRow(ctx, `SELECT first_name,last_name FROM users WHERE id=$1`, uid).
		Scan(&user.FirstName, &user.LastName)
	if errors.Is(err, pgx.ErrNoRows) {
		return nil, httpx.InvalidArgument(fmt.Sprintf("User not found: %d", uid))
	}
	if err != nil {
		return nil, err
	}
	entries, err := s.Entries(ctx, uid, year, month)
	if err != nil {
		return nil, err
	}
	return GeneratePDF(year, month, user, entries, true)
}

// CSV mirrors executeAsCsv: the Java caller supplies the user, possibly with null names.
// Java exposes only PDF over HTTP; there is no CSV endpoint in the specification.
func (s Service) CSV(ctx context.Context, uid int64, year, month int, user User) ([]byte, error) {
	entries, err := s.Entries(ctx, uid, year, month)
	if err != nil {
		return nil, err
	}
	return GenerateCSV(year, month, user, entries), nil
}
