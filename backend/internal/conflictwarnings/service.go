package conflictwarnings

import (
	"context"
	"fmt"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	openapi_types "github.com/oapi-codegen/runtime/types"
	"github.com/steffbo/timetrack/backend/internal/api"
	"github.com/steffbo/timetrack/backend/internal/clock"
	"github.com/steffbo/timetrack/backend/internal/db"
	"github.com/steffbo/timetrack/backend/internal/httpx"
	"github.com/steffbo/timetrack/backend/internal/recurringoffdays"
)

// Service holds the GetConflictWarnings and AcknowledgeConflictWarning use cases.
type Service struct {
	Pool  *pgxpool.Pool
	Clock clock.Clock
}

// ToResponse is RecurringOffDayConflictWarningMapper.toResponse (looks up the rule description).
func ToResponse(ctx context.Context, q db.Querier, w *Warning) (api.RecurringOffDayConflictWarningResponse, error) {
	created, updated := w.CreatedAt, w.UpdatedAt
	resp := api.RecurringOffDayConflictWarningResponse{Id: w.ID, UserId: w.UserID,
		ConflictDate: openapi_types.Date{Time: w.ConflictDate}, TimeEntryId: w.TimeEntryID,
		RecurringOffDayId: w.RecurringOffDayID, Acknowledged: w.Acknowledged,
		AcknowledgedAt: w.AcknowledgedAt, CreatedAt: &created, UpdatedAt: &updated}
	if w.RecurringOffDayID != nil {
		rod, err := recurringoffdays.FindByID(ctx, q, *w.RecurringOffDayID)
		if err != nil {
			return resp, err
		}
		if rod != nil {
			resp.RecurringOffDayDescription = rod.Description
		}
	}
	return resp, nil
}

// List is GetConflictWarnings.
func (s Service) List(
	ctx context.Context, userID int64, unacknowledgedOnly bool,
) ([]api.RecurringOffDayConflictWarningResponse, error) {
	var warnings []Warning
	var err error
	if unacknowledgedOnly {
		warnings, err = FindUnacknowledgedByUserID(ctx, s.Pool, userID)
	} else {
		warnings, err = FindByUserIDOrderByConflictDateDesc(ctx, s.Pool, userID)
	}
	if err != nil {
		return nil, err
	}
	out := make([]api.RecurringOffDayConflictWarningResponse, 0, len(warnings))
	for i := range warnings {
		resp, err := ToResponse(ctx, s.Pool, &warnings[i])
		if err != nil {
			return nil, err
		}
		out = append(out, resp)
	}
	return out, nil
}

// Acknowledge is AcknowledgeConflictWarning.
func (s Service) Acknowledge(
	ctx context.Context, userID, id int64,
) (api.RecurringOffDayConflictWarningResponse, error) {
	var warning *Warning
	err := db.Transaction(ctx, s.Pool, func(tx pgx.Tx) (err error) {
		if warning, err = FindByID(ctx, tx, id); err != nil {
			return err
		}
		if warning == nil {
			return httpx.InvalidArgument(fmt.Sprintf("Warning not found with ID: %d", id))
		}
		if warning.UserID != userID {
			return httpx.InvalidArgument("Warning does not belong to this user")
		}
		return Acknowledge(ctx, tx, warning, clock.LocalTimestamp(s.Clock.Now()))
	})
	if err != nil {
		return api.RecurringOffDayConflictWarningResponse{}, err
	}
	return ToResponse(ctx, s.Pool, warning)
}
