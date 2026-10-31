package workinghours

import (
	"context"
	"errors"
	"fmt"
	"math"
	"sort"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/steffbo/timetrack/backend-go/internal/api"
	"github.com/steffbo/timetrack/backend-go/internal/auth"
	"github.com/steffbo/timetrack/backend-go/internal/clock"
	"github.com/steffbo/timetrack/backend-go/internal/db"
	"github.com/steffbo/timetrack/backend-go/internal/domain"
	"github.com/steffbo/timetrack/backend-go/internal/httpx"
)

type Repository struct {
	Q     db.Querier
	Clock clock.Clock
}
type row struct {
	id         int64
	day        int
	hours      float64
	working    bool
	start, end *time.Time
	pause      int
}

func (r Repository) rows(ctx context.Context, id int64) ([]row, error) {
	rs, e := r.Q.Query(
		ctx,
		`SELECT id,weekday,hours,is_working_day,start_time,end_time,break_minutes
FROM working_hours WHERE user_id=$1`,
		id,
	)
	if e != nil {
		return nil, e
	}
	defer rs.Close()
	out := []row{}
	for rs.Next() {
		var x row
		if e = rs.Scan(&x.id, &x.day, &x.hours, &x.working, &x.start, &x.end, &x.pause); e != nil {
			return nil, e
		}
		out = append(out, x)
	}
	sort.Slice(out, func(i, j int) bool { return out[i].day < out[j].day })
	return out, rs.Err()
}
func config(x row) api.WorkingDayConfig {
	names := []api.WorkingDayConfigDayName{
		"",
		"MONDAY",
		"TUESDAY",
		"WEDNESDAY",
		"THURSDAY",
		"FRIDAY",
		"SATURDAY",
		"SUNDAY",
	}
	c := api.WorkingDayConfig{
		Id:           &x.id,
		Weekday:      x.day,
		Hours:        x.hours,
		IsWorkingDay: x.working,
		BreakMinutes: &x.pause,
		DayName:      &names[x.day],
	}
	if x.start != nil {
		s := localTimeString(*x.start)
		c.StartTime = &s
	}
	if x.end != nil {
		s := localTimeString(*x.end)
		c.EndTime = &s
	}
	return c
}

func localTimeString(t time.Time) string {
	if t.Second() == 0 && t.Nanosecond() == 0 {
		return t.Format("15:04")
	}
	if t.Nanosecond() == 0 {
		return t.Format("15:04:05")
	}
	return t.Format("15:04:05.999999999")
}
func (r Repository) Get(ctx context.Context, id int64) (api.WorkingHoursResponse, error) {
	if err := ensureUser(ctx, r.Q, id); err != nil {
		return api.WorkingHoursResponse{}, err
	}
	out, e := r.rows(ctx, id)
	if e != nil {
		return api.WorkingHoursResponse{}, e
	}
	if len(out) == 0 {
		return api.WorkingHoursResponse{}, httpx.NotFound(
			"USER_NOT_FOUND",
			"Arbeitsstunden-Konfiguration nicht gefunden",
		)
	}
	days := make([]api.WorkingDayConfig, 0, len(out))
	for _, x := range out {
		days = append(days, config(x))
	}
	return api.WorkingHoursResponse{UserId: id, WorkingDays: days}, nil
}
func (r Repository) Save(
	ctx context.Context,
	id int64,
	days []api.UpdateWorkingDayConfig,
) (api.WorkingHoursResponse, error) {
	if len(days) == 0 {
		return api.WorkingHoursResponse{}, httpx.InvalidArgument(
			"Arbeitsstunden-Konfiguration darf nicht leer sein",
		)
	}
	if len(days) != 7 {
		return api.WorkingHoursResponse{}, httpx.InvalidArgument(
			"Es müssen genau 7 Wochentage konfiguriert werden",
		)
	}
	seen := map[int]bool{}
	return r.save(ctx, id, days, seen)
}
func (r Repository) save(
	ctx context.Context,
	id int64,
	days []api.UpdateWorkingDayConfig,
	seen map[int]bool,
) (api.WorkingHoursResponse, error) {
	err := db.Transaction(ctx, r.Q.(*pgxpool.Pool), func(tx pgx.Tx) error {
		if e := ensureUser(ctx, tx, id); e != nil {
			return e
		}
		return saveDays(ctx, tx, r.Clock, id, days, seen)
	})
	if err != nil {
		return api.WorkingHoursResponse{}, err
	}
	return r.Get(ctx, id)
}
func saveDays(
	ctx context.Context,
	q db.Querier,
	c clock.Clock,
	id int64,
	days []api.UpdateWorkingDayConfig,
	seen map[int]bool,
) error {
	for _, d := range days {
		if d.Weekday < 1 || d.Weekday > 7 {
			return httpx.InvalidArgument("Wochentag muss zwischen 1 (Montag) und 7 (Sonntag) liegen")
		}
		if seen[d.Weekday] {
			return httpx.InvalidArgument(fmt.Sprintf("Doppelter Wochentag: %d", d.Weekday))
		}
		seen[d.Weekday] = true
		if (d.StartTime == nil) != (d.EndTime == nil) {
			return httpx.InvalidArgument("Start- und Endzeit müssen beide angegeben werden oder beide leer sein")
		}
		pause := 0
		if d.BreakMinutes != nil {
			pause = *d.BreakMinutes
		}
		if pause < 0 {
			return httpx.InvalidArgument("Pausenzeit darf nicht negativ sein")
		}
		hours := d.Hours
		var start, end any
		if d.StartTime != nil {
			st, e := time.Parse("15:04", *d.StartTime)
			if e != nil {
				st, e = time.Parse("15:04:05", *d.StartTime)
			}
			if e != nil {
				return httpx.InvalidArgument("Ungültiges Zeitformat: " + e.Error())
			}
			et, e := time.Parse("15:04", *d.EndTime)
			if e != nil {
				et, e = time.Parse("15:04:05", *d.EndTime)
			}
			if e != nil {
				return httpx.InvalidArgument("Ungültiges Zeitformat: " + e.Error())
			}
			if !et.After(st) {
				return httpx.InvalidArgument("Ungültiges Zeitformat: Endzeit muss nach Startzeit liegen")
			}
			mins := int(et.Sub(st).Minutes()) - pause
			if mins < 0 {
				mins = 0
			}
			hours = math.Round(float64(mins)/60*100) / 100
			start, end = st, et
		}
		if hours < 0 || hours > 24 {
			return httpx.InvalidArgument("Stunden müssen zwischen 0 und 24 liegen")
		}
		_, e := q.Exec(
			ctx,
			`INSERT INTO working_hours(
user_id,weekday,hours,is_working_day,start_time,end_time,break_minutes,updated_at
) VALUES($1,$2,$3,$4,$5,$6,$7,$8)
ON CONFLICT(user_id,weekday) DO UPDATE SET
hours=EXCLUDED.hours,is_working_day=EXCLUDED.is_working_day,
start_time=EXCLUDED.start_time,end_time=EXCLUDED.end_time,
break_minutes=EXCLUDED.break_minutes,updated_at=EXCLUDED.updated_at`,
			id,
			d.Weekday,
			hours,
			d.IsWorkingDay,
			start,
			end,
			pause,
			clock.LocalTimestamp(c.Now()),
		)
		if e != nil {
			return e
		}
	}
	if len(days) == 7 {
		for i := 1; i <= 7; i++ {
			if !seen[i] {
				return httpx.InvalidArgument(fmt.Sprintf("Fehlender Wochentag: %d", i))
			}
		}
	}
	return nil
}
func (r Repository) SaveDay(
	ctx context.Context,
	id int64,
	d api.UpdateWorkingDayConfig,
) (out api.WorkingDayConfig, err error) {
	if d.Weekday < 1 || d.Weekday > 7 {
		return out, httpx.InvalidArgument(
			"Wochentag muss zwischen 1 (Montag) und 7 (Sonntag) liegen",
		)
	}
	err = db.Transaction(ctx, r.Q.(*pgxpool.Pool), func(tx pgx.Tx) error {
		if e := ensureUser(ctx, tx, id); e != nil {
			return e
		}
		if e := saveDays(
			ctx,
			tx,
			r.Clock,
			id,
			[]api.UpdateWorkingDayConfig{d},
			map[int]bool{},
		); e != nil {
			return e
		}
		all, e := (Repository{Q: tx, Clock: r.Clock}).rows(ctx, id)
		if e != nil {
			return e
		}
		for _, x := range all {
			if x.day == d.Weekday {
				out = config(x)
				return nil
			}
		}
		return fmt.Errorf("working day missing after save")
	})
	return out, err
}

func ensureUser(ctx context.Context, q db.Querier, id int64) error {
	_, err := auth.FindUser(ctx, q, "id=$1", id)
	if errors.Is(err, pgx.ErrNoRows) {
		return httpx.NotFound("USER_NOT_FOUND", "Benutzer nicht gefunden")
	}
	return err
}
func OwnOrAdmin(p auth.Principal, id int64) error {
	if p.Role != domain.Admin && p.ID != id {
		return httpx.Forbidden("Keine Berechtigung, Arbeitsstunden anderer Benutzer anzuzeigen")
	}
	return nil
}
