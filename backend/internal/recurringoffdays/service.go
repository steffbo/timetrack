package recurringoffdays

import (
	"context"
	"fmt"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	openapi_types "github.com/oapi-codegen/runtime/types"
	"github.com/steffbo/timetrack/backend/internal/api"
	"github.com/steffbo/timetrack/backend/internal/auth"
	"github.com/steffbo/timetrack/backend/internal/clock"
	"github.com/steffbo/timetrack/backend/internal/db"
	"github.com/steffbo/timetrack/backend/internal/domain"
	"github.com/steffbo/timetrack/backend/internal/httpx"
)

// Service holds the use cases for recurring off-day rules and their exemptions.
type Service struct {
	Pool  *pgxpool.Pool
	Clock clock.Clock
}

func (s Service) now() time.Time { return clock.LocalTimestamp(s.Clock.Now()) }

// NotFound is the RecurringOffDayNotFoundException mapping.
func NotFound(id int64) *httpx.Error {
	return httpx.NotFound("RECURRING_OFF_DAY_NOT_FOUND",
		fmt.Sprintf("Recurring off-day not found with ID: %d", id))
}

// UserNotFound is UserService.getUserOrThrow's exception mapping.
func UserNotFound() *httpx.Error { return httpx.NotFound("USER_NOT_FOUND", "Benutzer nicht gefunden") }

func date(d *openapi_types.Date) *time.Time {
	if d == nil {
		return nil
	}
	t := d.Time
	return &t
}

func apiDate(t *time.Time) *openapi_types.Date {
	if t == nil {
		return nil
	}
	return &openapi_types.Date{Time: *t}
}

// ToResponse is RecurringOffDayMapper.toResponse.
func ToResponse(r *RecurringOffDay) api.RecurringOffDayResponse {
	created, updated := r.CreatedAt, r.UpdatedAt
	return api.RecurringOffDayResponse{Id: r.ID, UserId: r.UserID,
		RecurrencePattern: api.RecurringOffDayResponseRecurrencePattern(r.Pattern), Weekday: r.Weekday,
		WeekInterval: r.WeekInterval, ReferenceDate: apiDate(r.ReferenceDate), WeekOfMonth: r.WeekOfMonth,
		StartDate: openapi_types.Date{Time: r.StartDate}, EndDate: apiDate(r.EndDate), IsActive: r.IsActive,
		Description: r.Description, CreatedAt: &created, UpdatedAt: &updated}
}

func toExemptionResponse(e *Exemption) api.RecurringOffDayExemptionResponse {
	created := e.CreatedAt
	return api.RecurringOffDayExemptionResponse{Id: e.ID, RecurringOffDayId: e.RecurringOffDayID,
		ExemptionDate: openapi_types.Date{Time: e.Date}, Reason: e.Reason, CreatedAt: &created}
}

// List is GetRecurringOffDays.
func (s Service) List(ctx context.Context, userID int64) ([]api.RecurringOffDayResponse, error) {
	rods, err := FindByUserIDOrderByStartDateDesc(ctx, s.Pool, userID)
	if err != nil {
		return nil, err
	}
	out := make([]api.RecurringOffDayResponse, 0, len(rods))
	for i := range rods {
		out = append(out, ToResponse(&rods[i]))
	}
	return out, nil
}

func validateCreate(req api.CreateRecurringOffDayRequest) error {
	if req.Weekday < 1 || req.Weekday > 7 {
		return httpx.InvalidArgument("Wochentag muss zwischen 1 (Montag) und 7 (Sonntag) liegen")
	}
	switch domain.RecurrencePattern(req.RecurrencePattern) {
	case domain.EveryNthWeek:
		if req.WeekInterval == nil || *req.WeekInterval < 1 {
			return httpx.InvalidArgument("Wochenintervall ist erforderlich und muss mindestens 1 sein")
		}
		if req.ReferenceDate == nil {
			return httpx.InvalidArgument("Referenzdatum ist erforderlich")
		}
	case domain.NthWeekdayOfMonth:
		if req.WeekOfMonth == nil || *req.WeekOfMonth < 1 || *req.WeekOfMonth > 5 {
			return httpx.InvalidArgument("Woche des Monats muss zwischen 1 und 5 liegen")
		}
	}
	if req.EndDate != nil && req.EndDate.Before(req.StartDate.Time) {
		return httpx.InvalidArgument("Enddatum muss nach dem Startdatum liegen")
	}
	return nil
}

// Create is CreateRecurringOffDay.
func (s Service) Create(
	ctx context.Context, userID int64, req api.CreateRecurringOffDayRequest,
) (api.RecurringOffDayResponse, error) {
	if err := validateCreate(req); err != nil {
		return api.RecurringOffDayResponse{}, err
	}
	var rod RecurringOffDay
	err := db.Transaction(ctx, s.Pool, func(tx pgx.Tx) error {
		var exists bool
		if err := tx.QueryRow(ctx, `SELECT EXISTS(SELECT 1 FROM users WHERE id=$1)`, userID).
			Scan(&exists); err != nil {
			return err
		}
		if !exists {
			return UserNotFound()
		}
		rod = RecurringOffDay{UserID: userID, Pattern: domain.RecurrencePattern(req.RecurrencePattern),
			Weekday: req.Weekday, WeekInterval: req.WeekInterval, ReferenceDate: date(req.ReferenceDate),
			WeekOfMonth: req.WeekOfMonth, StartDate: req.StartDate.Time, EndDate: date(req.EndDate),
			IsActive: true, Description: req.Description}
		return Insert(ctx, tx, &rod, s.now())
	})
	if err != nil {
		return api.RecurringOffDayResponse{}, err
	}
	return ToResponse(&rod), nil
}

func (s Service) owned(ctx context.Context, q db.Querier, userID, id int64) (*RecurringOffDay, error) {
	rod, err := FindByID(ctx, q, id)
	if err != nil {
		return nil, err
	}
	if rod == nil {
		return nil, NotFound(id)
	}
	if err = auth.ValidateOwnership(&rod.UserID, &userID, "diese wiederkehrende Abwesenheit"); err != nil {
		return nil, err
	}
	return rod, nil
}

// Update is UpdateRecurringOffDay (null fields keep their value).
func (s Service) Update(
	ctx context.Context, userID, id int64, req api.UpdateRecurringOffDayRequest,
) (api.RecurringOffDayResponse, error) {
	var rod *RecurringOffDay
	err := db.Transaction(ctx, s.Pool, func(tx pgx.Tx) (err error) {
		if rod, err = s.owned(ctx, tx, userID, id); err != nil {
			return err
		}
		if req.RecurrencePattern != nil {
			rod.Pattern = domain.RecurrencePattern(*req.RecurrencePattern)
		}
		if req.Weekday != nil {
			rod.Weekday = *req.Weekday
		}
		if req.WeekInterval != nil {
			rod.WeekInterval = req.WeekInterval
		}
		if req.ReferenceDate != nil {
			rod.ReferenceDate = date(req.ReferenceDate)
		}
		if req.WeekOfMonth != nil {
			rod.WeekOfMonth = req.WeekOfMonth
		}
		if req.StartDate != nil {
			rod.StartDate = req.StartDate.Time
		}
		if req.EndDate != nil {
			rod.EndDate = date(req.EndDate)
		}
		if req.IsActive != nil {
			rod.IsActive = *req.IsActive
		}
		if req.Description != nil {
			rod.Description = req.Description
		}
		return Update(ctx, tx, rod, s.now())
	})
	if err != nil {
		return api.RecurringOffDayResponse{}, err
	}
	return ToResponse(rod), nil
}

// Delete is DeleteRecurringOffDay.
func (s Service) Delete(ctx context.Context, userID, id int64) error {
	return db.Transaction(ctx, s.Pool, func(tx pgx.Tx) error {
		if _, err := s.owned(ctx, tx, userID, id); err != nil {
			return err
		}
		return Delete(ctx, tx, id)
	})
}

// ownedForExemptions replicates the IllegalArgumentException checks of the exemption use cases.
func ownedForExemptions(ctx context.Context, q db.Querier, userID, id int64) (*RecurringOffDay, error) {
	rod, err := FindByID(ctx, q, id)
	if err != nil {
		return nil, err
	}
	if rod == nil {
		return nil, httpx.InvalidArgument(fmt.Sprintf("Recurring off-day not found with ID: %d", id))
	}
	if rod.UserID != userID {
		return nil, httpx.InvalidArgument(fmt.Sprintf("Recurring off-day does not belong to user: %d", userID))
	}
	return rod, nil
}

// Exemptions is GetRecurringOffDayExemptions.
func (s Service) Exemptions(
	ctx context.Context, userID, id int64,
) ([]api.RecurringOffDayExemptionResponse, error) {
	if _, err := ownedForExemptions(ctx, s.Pool, userID, id); err != nil {
		return nil, err
	}
	list, err := FindExemptions(ctx, s.Pool, id)
	if err != nil {
		return nil, err
	}
	out := make([]api.RecurringOffDayExemptionResponse, 0, len(list))
	for i := range list {
		out = append(out, toExemptionResponse(&list[i]))
	}
	return out, nil
}

// CreateExemption is CreateRecurringOffDayExemption.
func (s Service) CreateExemption(
	ctx context.Context, userID, id int64, req api.CreateRecurringOffDayExemptionRequest,
) (api.RecurringOffDayExemptionResponse, error) {
	var exemption Exemption
	err := db.Transaction(ctx, s.Pool, func(tx pgx.Tx) error {
		rod, err := ownedForExemptions(ctx, tx, userID, id)
		if err != nil {
			return err
		}
		day := req.ExemptionDate.Time
		if !AppliesToDate(rod, day) {
			return httpx.InvalidArgument(fmt.Sprintf(
				"Date %s does not match the recurring off-day pattern", day.Format(time.DateOnly)))
		}
		exists, err := ExemptionExists(ctx, tx, id, day)
		if err != nil {
			return err
		}
		if exists {
			return httpx.Conflict("EXEMPTION_ALREADY_EXISTS",
				"Exemption already exists for date: "+day.Format(time.DateOnly))
		}
		exemption = Exemption{RecurringOffDayID: id, Date: day, Reason: req.Reason}
		return InsertExemption(ctx, tx, &exemption, s.now())
	})
	if err != nil {
		return api.RecurringOffDayExemptionResponse{}, err
	}
	return toExemptionResponse(&exemption), nil
}

// DeleteExemption is DeleteRecurringOffDayExemption.
func (s Service) DeleteExemption(ctx context.Context, userID, id, exemptionID int64) error {
	return db.Transaction(ctx, s.Pool, func(tx pgx.Tx) error {
		if _, err := ownedForExemptions(ctx, tx, userID, id); err != nil {
			return err
		}
		exemption, err := FindExemptionByID(ctx, tx, exemptionID)
		if err != nil {
			return err
		}
		if exemption == nil {
			return httpx.InvalidArgument(fmt.Sprintf("Exemption not found with ID: %d", exemptionID))
		}
		if exemption.RecurringOffDayID != id {
			return httpx.InvalidArgument(
				fmt.Sprintf("Exemption does not belong to recurring off-day: %d", id))
		}
		return DeleteExemption(ctx, tx, exemptionID)
	})
}
