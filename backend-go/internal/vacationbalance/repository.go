package vacationbalance

import (
	"context"
	"errors"
	"fmt"
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
	Pool  *pgxpool.Pool
	Clock clock.Clock
}

func (r Repository) WorkingDays(
	ctx context.Context,
	userID int64,
	start, end time.Time,
) (float64, error) {
	if start.After(end) {
		return 0, nil
	}
	var state domain.GermanState
	var halfDays bool
	err := r.Pool.QueryRow(
		ctx,
		`SELECT state,half_day_holidays_enabled FROM users WHERE id=$1`,
		userID,
	).Scan(&state, &halfDays)
	if err != nil {
		return 0, err
	}
	return calculate(ctx, r.Pool, userID, state, start, end, halfDays)
}

func (r Repository) Balance(
	ctx context.Context,
	uid int64,
	year int,
) (api.VacationBalanceResponse, error) {
	var out api.VacationBalanceResponse
	err := db.Transaction(ctx, r.Pool, func(tx pgx.Tx) error {
		var id int64
		var annual, carry, adjust, used float64
		var created, updated time.Time
		e := tx.QueryRow(
			ctx,
			`SELECT id,annual_allowance_days,carried_over_days,adjustment_days,
used_days,created_at,updated_at
FROM vacation_balance WHERE user_id=$1 AND year=$2`,
			uid,
			year,
		).Scan(&id, &annual, &carry, &adjust, &used, &created, &updated)
		if e != nil {
			if e != pgx.ErrNoRows {
				return e
			}
			if _, e = auth.FindUser(ctx, tx, "id=$1", uid); e != nil {
				return fmt.Errorf("User not found with ID: %d", uid)
			}
			e = tx.QueryRow(
				ctx,
				`INSERT INTO vacation_balance(
user_id,year,annual_allowance_days,carried_over_days,
adjustment_days,used_days,remaining_days
) VALUES($1,$2,30,0,0,0,30)
RETURNING id,annual_allowance_days,carried_over_days,adjustment_days,
used_days,created_at,updated_at`,
				uid,
				year,
			).Scan(&id, &annual, &carry, &adjust, &used, &created, &updated)
			if e != nil {
				return e
			}
		}
		usedDays, planned, e := r.totals(ctx, tx, uid, year)
		if e != nil {
			return e
		}
		used = usedDays
		updated = clock.LocalTimestamp(r.Clock.Now())
		_, e = tx.Exec(
			ctx,
			`UPDATE vacation_balance
SET used_days=$1,remaining_days=$2,updated_at=$3 WHERE id=$4`,
			used,
			annual+carry+adjust-used,
			updated,
			id,
		)
		if e != nil {
			return e
		}
		out = api.VacationBalanceResponse{
			Id:                  id,
			UserId:              uid,
			Year:                year,
			AnnualAllowanceDays: annual,
			CarriedOverDays:     carry,
			AdjustmentDays:      adjust,
			UsedDays:            used,
			PlannedDays:         planned,
			RemainingDays:       annual + carry + adjust - planned,
			CreatedAt:           &created,
			UpdatedAt:           &updated,
		}
		return nil
	})
	return out, err
}
func (r Repository) Update(
	ctx context.Context,
	p auth.Principal,
	in api.UpdateVacationBalanceRequest,
) (api.VacationBalanceResponse, error) {
	if p.Role != domain.Admin && p.ID != in.UserId {
		return api.VacationBalanceResponse{}, errors.New("You can only update your own vacation balance")
	}
	var out api.VacationBalanceResponse
	err := db.Transaction(ctx, r.Pool, func(tx pgx.Tx) error {
		var a, c, d, u float64
		var id int64
		var cr, up time.Time
		e := tx.QueryRow(
			ctx,
			`SELECT id,annual_allowance_days,carried_over_days,adjustment_days,
used_days,created_at,updated_at
FROM vacation_balance WHERE user_id=$1 AND year=$2`,
			in.UserId,
			in.Year,
		).Scan(&id, &a, &c, &d, &u, &cr, &up)
		if e == pgx.ErrNoRows {
			if _, e = auth.FindUser(ctx, tx, "id=$1", in.UserId); e != nil {
				return httpx.NotFound("USER_NOT_FOUND", "User not found with ID: "+itoa(in.UserId))
			}
			e = tx.QueryRow(
				ctx,
				`INSERT INTO vacation_balance(
user_id,year,annual_allowance_days,carried_over_days,
adjustment_days,used_days,remaining_days
) VALUES($1,$2,30,0,0,0,30)
RETURNING id,annual_allowance_days,carried_over_days,adjustment_days,
used_days,created_at,updated_at`,
				in.UserId,
				in.Year,
			).Scan(&id, &a, &c, &d, &u, &cr, &up)
		}
		if e != nil {
			return e
		}
		if in.AnnualAllowanceDays != nil {
			a = *in.AnnualAllowanceDays
		}
		if in.CarriedOverDays != nil {
			c = *in.CarriedOverDays
		}
		if in.AdjustmentDays != nil {
			d = *in.AdjustmentDays
		}
		up = clock.LocalTimestamp(r.Clock.Now())
		_, e = tx.Exec(
			ctx,
			`UPDATE vacation_balance SET annual_allowance_days=$1,
carried_over_days=$2,adjustment_days=$3,remaining_days=$4,updated_at=$5
WHERE id=$6`,
			a,
			c,
			d,
			a+c+d-u,
			up,
			id,
		)
		if e != nil {
			return e
		}
		_, planned, e := r.totals(ctx, tx, in.UserId, in.Year)
		if e != nil {
			return e
		}
		out = api.VacationBalanceResponse{
			Id:                  id,
			UserId:              in.UserId,
			Year:                in.Year,
			AnnualAllowanceDays: a,
			CarriedOverDays:     c,
			AdjustmentDays:      d,
			UsedDays:            u,
			PlannedDays:         planned,
			RemainingDays:       a + c + d - planned,
			CreatedAt:           &cr,
			UpdatedAt:           &up,
		}
		return nil
	})
	return out, err
}
func (r Repository) totals(
	ctx context.Context,
	q db.Querier,
	uid int64,
	year int,
) (float64, float64, error) {
	var state string
	var half bool
	e := q.QueryRow(
		ctx,
		`SELECT state,half_day_holidays_enabled FROM users WHERE id=$1`,
		uid,
	).Scan(&state, &half)
	if e != nil {
		return 0, 0, e
	}
	var used, planned float64
	today := r.Clock.Now().Format("2006-01-02")
	yearStart := time.Date(year, 1, 1, 0, 0, 0, 0, time.UTC)
	yearEnd := time.Date(year, 12, 31, 0, 0, 0, 0, time.UTC)
	rows, e := q.Query(
		ctx,
		`SELECT start_date,end_date FROM time_off
WHERE user_id=$1 AND time_off_type='VACATION'
AND start_date<=$3 AND end_date>=$2`,
		uid,
		yearStart,
		yearEnd,
	)
	if e != nil {
		return 0, 0, e
	}
	defer rows.Close()
	type span struct{ start, end time.Time }
	spans := []span{}
	for rows.Next() {
		var a, b time.Time
		if e = rows.Scan(&a, &b); e != nil {
			return 0, 0, e
		}
		spans = append(spans, span{a, b})
	}
	if e = rows.Err(); e != nil {
		return 0, 0, e
	}
	rows.Close()
	for _, s := range spans {
		days, err := calculate(
			ctx,
			q,
			uid,
			domain.GermanState(state),
			s.start,
			s.end,
			half,
		)
		if err != nil {
			return 0, 0, err
		}
		planned += days
		if s.end.Format("2006-01-02") <= today {
			used += days
		}
	}
	return used, planned, nil
}

func calculate(
	ctx context.Context,
	q db.Querier,
	uid int64,
	state domain.GermanState,
	start time.Time,
	end time.Time,
	half bool,
) (float64, error) {
	in := domain.WorkingDayInput{
		WorkingWeekdays: map[time.Weekday]bool{},
		Holidays:        map[string]bool{},
		RecurringOff:    map[string]bool{},
		HalfDaysEnabled: half,
	}
	rs, e := q.Query(ctx, `SELECT weekday FROM working_hours WHERE user_id=$1 AND is_working_day=true`, uid)
	if e != nil {
		return 0, e
	}
	for rs.Next() {
		var n int
		if err := rs.Scan(&n); err != nil {
			rs.Close()
			return 0, err
		}
		in.WorkingWeekdays[time.Weekday(n%7)] = true
	}
	if err := rs.Err(); err != nil {
		rs.Close()
		return 0, err
	}
	rs.Close()
	for year := start.Year(); year <= end.Year(); year++ {
		for _, d := range (domain.GermanPublicHolidays{}).GetPublicHolidays(year, state) {
			in.Holidays[d.Format(time.DateOnly)] = true
		}
	}
	others, e := q.Query(
		ctx,
		`SELECT start_date,end_date FROM time_off
WHERE user_id=$1 AND time_off_type<>'VACATION'
AND start_date<=$3 AND end_date>=$2`,
		uid,
		start,
		end,
	)
	if e != nil {
		return 0, e
	}
	for others.Next() {
		var a, b time.Time
		if err := others.Scan(&a, &b); err != nil {
			others.Close()
			return 0, err
		}
		in.OtherTimeOff = append(in.OtherTimeOff, domain.DateRange{Start: a, End: b})
	}
	if err := others.Err(); err != nil {
		others.Close()
		return 0, err
	}
	others.Close()
	in.RecurringOff, e = recurringOffDates(ctx, q, uid, start, end)
	if e != nil {
		return 0, e
	}
	return domain.WorkingDays(start, end, in), nil
}

func recurringOffDates(
	ctx context.Context,
	q db.Querier,
	uid int64,
	start time.Time,
	end time.Time,
) (map[string]bool, error) {
	out := map[string]bool{}
	rows, e := q.Query(
		ctx,
		`SELECT id,recurrence_pattern,weekday,week_interval,reference_date,
week_of_month,start_date,end_date
FROM recurring_off_days
WHERE user_id=$1 AND is_active=true AND start_date<=$3
AND (end_date IS NULL OR end_date>=$2)`,
		uid,
		start,
		end,
	)
	if e != nil {
		return nil, e
	}
	type rule struct {
		id        int64
		pattern   string
		weekday   int
		interval  *int
		reference *time.Time
		week      *int
		start     time.Time
		end       *time.Time
	}
	rules := []rule{}
	for rows.Next() {
		var x rule
		if err := rows.Scan(
			&x.id,
			&x.pattern,
			&x.weekday,
			&x.interval,
			&x.reference,
			&x.week,
			&x.start,
			&x.end,
		); err != nil {
			rows.Close()
			return nil, err
		}
		rules = append(rules, x)
	}
	if err := rows.Err(); err != nil {
		rows.Close()
		return nil, err
	}
	rows.Close()
	for d := start; !d.After(end); d = d.AddDate(0, 0, 1) {
		for _, x := range rules {
			if d.Before(x.start) || (x.end != nil && d.After(*x.end)) || int(d.Weekday()) != x.weekday%7 {
				continue
			}
			match := false
			if x.pattern == "NTH_WEEKDAY_OF_MONTH" && x.week != nil {
				n := (d.Day()-1)/7 + 1
				last := d.AddDate(0, 0, 7).Month() != d.Month()
				match = n == *x.week || (*x.week == 5 && last)
			}
			if x.pattern == "EVERY_NTH_WEEK" && x.interval != nil && x.reference != nil && *x.interval > 0 {
				weeks := int(d.Sub(*x.reference).Hours() / 24 / 7)
				match = weeks >= 0 && weeks%*x.interval == 0
			}
			if !match {
				continue
			}
			var exists bool
			err := q.QueryRow(
				ctx,
				`SELECT EXISTS(
SELECT 1 FROM recurring_off_day_exemptions
WHERE recurring_off_day_id=$1 AND exemption_date=$2
)`,
				x.id,
				d,
			).Scan(&exists)
			if err != nil {
				return nil, err
			}
			if !exists {
				out[d.Format(time.DateOnly)] = true
			}
		}
	}
	return out, nil
}
func itoa(v int64) string { return fmt.Sprint(v) }
