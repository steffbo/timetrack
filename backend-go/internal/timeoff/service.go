package timeoff

import (
	"context"
	"fmt"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	openapi_types "github.com/oapi-codegen/runtime/types"
	"github.com/steffbo/timetrack/backend-go/internal/api"
	"github.com/steffbo/timetrack/backend-go/internal/auth"
	"github.com/steffbo/timetrack/backend-go/internal/clock"
	"github.com/steffbo/timetrack/backend-go/internal/db"
	"github.com/steffbo/timetrack/backend-go/internal/domain"
	"github.com/steffbo/timetrack/backend-go/internal/httpx"
)

// Service holds the time-off use cases.
type Service struct {
	Pool  *pgxpool.Pool
	Clock clock.Clock
}

func (s Service) now() time.Time { return clock.LocalTimestamp(s.Clock.Now()) }

// NotFound is the TimeOffNotFoundException mapping.
func NotFound(id int64) *httpx.Error {
	return httpx.NotFound("TIME_OFF_NOT_FOUND", fmt.Sprintf("Time-off entry not found with ID: %d", id))
}

func (s Service) recalculate(ctx context.Context, q db.Querier, userID int64, start, end time.Time) error {
	return RecalculateVacationBalanceForDateRange(ctx, q, userID, start, end, s.Clock.Now())
}

// toResponse is TimeOffMapper.toResponse; days are calculated excluding the entry itself.
func (s Service) toResponse(ctx context.Context, q db.Querier, t *TimeOff) (api.TimeOffResponse, error) {
	var state domain.GermanState
	if err := q.QueryRow(ctx, `SELECT state FROM users WHERE id=$1`, t.UserID).Scan(&state); err != nil {
		return api.TimeOffResponse{}, err
	}
	days, err := WorkingDays(ctx, q, t.UserID, state, t.StartDate, t.EndDate, t.ID)
	if err != nil {
		return api.TimeOffResponse{}, err
	}
	confirmed, created, updated := t.Confirmed, t.CreatedAt, t.UpdatedAt
	return api.TimeOffResponse{Id: t.ID, UserId: t.UserID, StartDate: openapi_types.Date{Time: t.StartDate},
		EndDate: openapi_types.Date{Time: t.EndDate}, TimeOffType: api.TimeOffResponseTimeOffType(t.Type),
		Days: days, HoursPerDay: t.HoursPerDay, Notes: t.Notes, Confirmed: &confirmed,
		CreatedAt: &created, UpdatedAt: &updated}, nil
}

// List is GetTimeOffEntries: with both dates a range filter, otherwise all entries.
func (s Service) List(
	ctx context.Context, userID int64, start, end *openapi_types.Date,
) ([]api.TimeOffResponse, error) {
	var entries []TimeOff
	var err error
	if start != nil && end != nil {
		entries, err = FindByUserIDAndDateRange(ctx, s.Pool, userID, start.Time, end.Time)
	} else {
		entries, err = FindByUserIDOrderByStartDateDesc(ctx, s.Pool, userID)
	}
	if err != nil {
		return nil, err
	}
	out := make([]api.TimeOffResponse, 0, len(entries))
	for i := range entries {
		resp, err := s.toResponse(ctx, s.Pool, &entries[i])
		if err != nil {
			return nil, err
		}
		out = append(out, resp)
	}
	return out, nil
}

// Create is CreateTimeOff.
func (s Service) Create(
	ctx context.Context, userID int64, req api.CreateTimeOffRequest,
) (api.TimeOffResponse, error) {
	if req.EndDate.Before(req.StartDate.Time) {
		return api.TimeOffResponse{}, httpx.InvalidArgument("Enddatum muss nach dem Startdatum liegen")
	}
	if req.HoursPerDay != nil && *req.HoursPerDay < 0 {
		return api.TimeOffResponse{}, httpx.InvalidArgument("Stunden pro Tag dürfen nicht negativ sein")
	}
	var resp api.TimeOffResponse
	err := db.Transaction(ctx, s.Pool, func(tx pgx.Tx) error {
		var exists bool
		if err := tx.QueryRow(ctx, `SELECT EXISTS(SELECT 1 FROM users WHERE id=$1)`, userID).
			Scan(&exists); err != nil {
			return err
		}
		if !exists {
			return httpx.NotFound("USER_NOT_FOUND", "Benutzer nicht gefunden")
		}
		entry := TimeOff{UserID: userID, StartDate: req.StartDate.Time, EndDate: req.EndDate.Time,
			Type: domain.TimeOffType(req.TimeOffType), HoursPerDay: req.HoursPerDay, Notes: req.Notes}
		if req.Confirmed != nil {
			entry.Confirmed = *req.Confirmed
		}
		if err := Insert(ctx, tx, &entry, s.now()); err != nil {
			return err
		}
		if entry.Type == domain.Vacation {
			err := s.recalculate(ctx, tx, userID, entry.StartDate, entry.EndDate)
			if err != nil {
				return err
			}
		}
		var err error
		resp, err = s.toResponse(ctx, tx, &entry)
		return err
	})
	return resp, err
}

func owned(ctx context.Context, q db.Querier, userID, id int64) (*TimeOff, error) {
	entry, err := FindByID(ctx, q, id)
	if err != nil {
		return nil, err
	}
	if entry == nil {
		return nil, NotFound(id)
	}
	if err = auth.ValidateOwnership(&entry.UserID, &userID, "diesen Abwesenheitseintrag"); err != nil {
		return nil, err
	}
	return entry, nil
}

// Update is UpdateTimeOff (null fields keep their value; no date validation, the DB check applies).
func (s Service) Update(
	ctx context.Context, userID, id int64, req api.UpdateTimeOffRequest,
) (api.TimeOffResponse, error) {
	var resp api.TimeOffResponse
	err := db.Transaction(ctx, s.Pool, func(tx pgx.Tx) error {
		entry, err := owned(ctx, tx, userID, id)
		if err != nil {
			return err
		}
		oldType, oldStartYear, oldEndYear := entry.Type, entry.StartDate.Year(), entry.EndDate.Year()
		if req.StartDate != nil {
			entry.StartDate = req.StartDate.Time
		}
		if req.EndDate != nil {
			entry.EndDate = req.EndDate.Time
		}
		if req.TimeOffType != nil {
			entry.Type = domain.TimeOffType(*req.TimeOffType)
		}
		if req.HoursPerDay != nil {
			entry.HoursPerDay = req.HoursPerDay
		}
		if req.Notes != nil {
			entry.Notes = req.Notes
		}
		if req.Confirmed != nil {
			entry.Confirmed = *req.Confirmed
		}
		if err = Update(ctx, tx, entry, s.now()); err != nil {
			return err
		}
		if oldType == domain.Vacation || entry.Type == domain.Vacation {
			earliest := utcDate(oldStartYear, time.January, 1)
			if entry.StartDate.Before(earliest) {
				earliest = entry.StartDate
			}
			latest := utcDate(oldEndYear, time.December, 31)
			if entry.EndDate.After(latest) {
				latest = entry.EndDate
			}
			if err = s.recalculate(ctx, tx, userID, earliest, latest); err != nil {
				return err
			}
		}
		resp, err = s.toResponse(ctx, tx, entry)
		return err
	})
	return resp, err
}

// Delete is DeleteTimeOff.
func (s Service) Delete(ctx context.Context, userID, id int64) error {
	return db.Transaction(ctx, s.Pool, func(tx pgx.Tx) error {
		entry, err := owned(ctx, tx, userID, id)
		if err != nil {
			return err
		}
		if err = Delete(ctx, tx, id); err != nil {
			return err
		}
		if entry.Type == domain.Vacation {
			return s.recalculate(ctx, tx, userID, entry.StartDate, entry.EndDate)
		}
		return nil
	})
}
