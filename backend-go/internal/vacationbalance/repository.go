package vacationbalance

import (
	"context"
	"errors"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/steffbo/timetrack/backend-go/internal/api"
	"github.com/steffbo/timetrack/backend-go/internal/auth"
	"github.com/steffbo/timetrack/backend-go/internal/clock"
	"github.com/steffbo/timetrack/backend-go/internal/db"
	"github.com/steffbo/timetrack/backend-go/internal/domain"
	"github.com/steffbo/timetrack/backend-go/internal/httpx"
	"github.com/steffbo/timetrack/backend-go/internal/users"
)

type Repository struct {
	Pool  *pgxpool.Pool
	Clock clock.Clock
}

type balance struct {
	ID, UserID                      int64
	Year                            int
	Annual, Carry, Adjustment, Used float64
	Created, Updated                time.Time
}

func (b balance) remaining(days float64) float64 { return b.Annual + b.Carry + b.Adjustment - days }

// save mirrors Hibernate dirty checking for both recalculation and explicit updates.
func (b *balance) save(ctx context.Context, q db.Querier, now time.Time) error {
	var updated time.Time
	err := q.QueryRow(ctx, `UPDATE vacation_balance SET annual_allowance_days=$2,
  carried_over_days=$3,adjustment_days=$4,used_days=$5,remaining_days=$6,updated_at=$7
  WHERE id=$1 AND (annual_allowance_days IS DISTINCT FROM $2 OR carried_over_days IS DISTINCT FROM $3
   OR adjustment_days IS DISTINCT FROM $4 OR used_days IS DISTINCT FROM $5
   OR remaining_days IS DISTINCT FROM $6) RETURNING updated_at`, b.ID, b.Annual, b.Carry,
		b.Adjustment, b.Used, b.remaining(b.Used), clock.LocalTimestamp(now)).Scan(&updated)
	if errors.Is(err, pgx.ErrNoRows) {
		return nil
	}
	if err == nil {
		b.Updated = updated
	}
	return err
}

const balanceColumns = `id,user_id,year,annual_allowance_days,carried_over_days,
 adjustment_days,used_days,created_at,updated_at`

func scanBalance(row pgx.Row) (balance, error) {
	var b balance
	err := row.Scan(&b.ID, &b.UserID, &b.Year, &b.Annual, &b.Carry, &b.Adjustment, &b.Used,
		&b.Created, &b.Updated)
	return b, err
}

func findOrCreate(ctx context.Context, q db.Querier, uid int64, year int, now time.Time) (balance, error) {
	b, err := scanBalance(q.QueryRow(ctx, `SELECT `+balanceColumns+
		` FROM vacation_balance WHERE user_id=$1 AND year=$2`, uid, year))
	if !errors.Is(err, pgx.ErrNoRows) {
		return b, err
	}
	if _, err := userForCalculation(ctx, q, uid); err != nil {
		return b, err
	}
	return scanBalance(q.QueryRow(ctx, `INSERT INTO vacation_balance
  (user_id,year,annual_allowance_days,carried_over_days,adjustment_days,used_days,
   remaining_days,created_at,updated_at) VALUES($1,$2,$3,0,0,0,$3,$4,$4) RETURNING `+balanceColumns,
		uid, year, defaultAnnualAllowanceDays, clock.LocalTimestamp(now)))
}

// response is VacationBalanceMapper: stored used days, dynamically calculated planned days.
func response(
	ctx context.Context, q db.Querier, b balance, now time.Time,
) (api.VacationBalanceResponse, error) {
	_, planned, err := totals(ctx, q, b.UserID, b.Year, now)
	if err != nil {
		return api.VacationBalanceResponse{}, err
	}
	return api.VacationBalanceResponse{Id: b.ID, UserId: b.UserID, Year: b.Year,
		AnnualAllowanceDays: b.Annual, CarriedOverDays: b.Carry, AdjustmentDays: b.Adjustment,
		UsedDays: b.Used, PlannedDays: planned, RemainingDays: b.remaining(planned),
		CreatedAt: &b.Created, UpdatedAt: &b.Updated}, nil
}

func (r Repository) WorkingDays(ctx context.Context, uid int64, start, end time.Time) (float64, error) {
	if start.After(end) {
		return 0, nil
	}
	user, err := userForCalculation(ctx, r.Pool, uid)
	if err != nil {
		return 0, err
	}
	return WorkingDays(ctx, r.Pool, uid, user.State, start, end, 0)
}

func (r Repository) Balance(ctx context.Context, uid int64, year int) (api.VacationBalanceResponse, error) {
	var out api.VacationBalanceResponse
	err := db.Transaction(ctx, r.Pool, func(tx pgx.Tx) error {
		if err := recalculateVacationBalance(ctx, tx, uid, year, r.Clock.Now()); err != nil {
			return err
		}
		b, err := findOrCreate(ctx, tx, uid, year, r.Clock.Now())
		if err != nil {
			return err
		}
		out, err = response(ctx, tx, b, r.Clock.Now())
		return err
	})
	return out, err
}

func (r Repository) Update(
	ctx context.Context, p auth.Principal, in api.UpdateVacationBalanceRequest,
) (api.VacationBalanceResponse, error) {
	if p.Role != domain.Admin && p.ID != in.UserId {
		return api.VacationBalanceResponse{}, errors.New("You can only update your own vacation balance")
	}
	var out api.VacationBalanceResponse
	err := db.Transaction(ctx, r.Pool, func(tx pgx.Tx) error {
		_, found, err := (users.Repository{Q: tx}).FindByID(ctx, in.UserId)
		if err != nil {
			return err
		}
		if !found {
			return httpx.NotFound("USER_NOT_FOUND", userNotFoundMessage(in.UserId))
		}
		b, err := findOrCreate(ctx, tx, in.UserId, in.Year, r.Clock.Now())
		if err != nil {
			return err
		}
		if in.AnnualAllowanceDays != nil {
			b.Annual = *in.AnnualAllowanceDays
		}
		if in.CarriedOverDays != nil {
			b.Carry = *in.CarriedOverDays
		}
		if in.AdjustmentDays != nil {
			b.Adjustment = *in.AdjustmentDays
		}
		if err := b.save(ctx, tx, r.Clock.Now()); err != nil {
			return err
		}
		out, err = response(ctx, tx, b, r.Clock.Now())
		return err
	})
	return out, err
}
