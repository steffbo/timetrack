package server

import (
	"context"

	"github.com/steffbo/timetrack/backend/internal/api"
	"github.com/steffbo/timetrack/backend/internal/auth"
	"github.com/steffbo/timetrack/backend/internal/httpx"
	"github.com/steffbo/timetrack/backend/internal/recurringoffdays"
)

func (s *Server) recurringOffDays() recurringoffdays.Service {
	return recurringoffdays.Service{Pool: s.Pool, Clock: s.Clock}
}

func (s *Server) GetRecurringOffDays(
	ctx context.Context,
	r api.GetRecurringOffDaysRequestObject,
) (api.GetRecurringOffDaysResponseObject, error) {
	principal, _ := auth.Current(ctx)
	out, err := s.recurringOffDays().List(ctx, principal.ID)
	if err != nil {
		return nil, err
	}
	return api.GetRecurringOffDays200JSONResponse(out), nil
}

func (s *Server) CreateRecurringOffDay(
	ctx context.Context,
	r api.CreateRecurringOffDayRequestObject,
) (api.CreateRecurringOffDayResponseObject, error) {
	if r.Body == nil {
		return nil, httpx.InvalidArgument("Request body is missing")
	}
	principal, _ := auth.Current(ctx)
	out, err := s.recurringOffDays().Create(ctx, principal.ID, *r.Body)
	if err != nil {
		return nil, err
	}
	return api.CreateRecurringOffDay201JSONResponse(out), nil
}

func (s *Server) DeleteRecurringOffDay(
	ctx context.Context,
	r api.DeleteRecurringOffDayRequestObject,
) (api.DeleteRecurringOffDayResponseObject, error) {
	principal, _ := auth.Current(ctx)
	if err := s.recurringOffDays().Delete(ctx, principal.ID, r.Id); err != nil {
		return nil, err
	}
	return api.DeleteRecurringOffDay204Response{}, nil
}

func (s *Server) UpdateRecurringOffDay(
	ctx context.Context,
	r api.UpdateRecurringOffDayRequestObject,
) (api.UpdateRecurringOffDayResponseObject, error) {
	if r.Body == nil {
		return nil, httpx.InvalidArgument("Request body is missing")
	}
	principal, _ := auth.Current(ctx)
	out, err := s.recurringOffDays().Update(ctx, principal.ID, r.Id, *r.Body)
	if err != nil {
		return nil, err
	}
	return api.UpdateRecurringOffDay200JSONResponse(out), nil
}

func (s *Server) GetExemptions(
	ctx context.Context,
	r api.GetExemptionsRequestObject,
) (api.GetExemptionsResponseObject, error) {
	principal, _ := auth.Current(ctx)
	out, err := s.recurringOffDays().Exemptions(ctx, principal.ID, r.Id)
	if err != nil {
		return nil, err
	}
	return api.GetExemptions200JSONResponse(out), nil
}

func (s *Server) CreateExemption(
	ctx context.Context,
	r api.CreateExemptionRequestObject,
) (api.CreateExemptionResponseObject, error) {
	if r.Body == nil {
		return nil, httpx.InvalidArgument("Request body is missing")
	}
	principal, _ := auth.Current(ctx)
	out, err := s.recurringOffDays().CreateExemption(ctx, principal.ID, r.Id, *r.Body)
	if err != nil {
		return nil, err
	}
	return api.CreateExemption201JSONResponse(out), nil
}

func (s *Server) DeleteExemption(
	ctx context.Context,
	r api.DeleteExemptionRequestObject,
) (api.DeleteExemptionResponseObject, error) {
	principal, _ := auth.Current(ctx)
	service := s.recurringOffDays()
	if err := service.DeleteExemption(ctx, principal.ID, r.RecurringOffDayId, r.ExemptionId); err != nil {
		return nil, err
	}
	return api.DeleteExemption204Response{}, nil
}
