package server

import (
	"context"

	"github.com/steffbo/timetrack/backend-go/internal/api"
	"github.com/steffbo/timetrack/backend-go/internal/httpx"
)

func (s *Server) GetRecurringOffDays(
	ctx context.Context,
	r api.GetRecurringOffDaysRequestObject,
) (api.GetRecurringOffDaysResponseObject, error) {
	return nil, httpx.NotImplemented()
}

func (s *Server) CreateRecurringOffDay(
	ctx context.Context,
	r api.CreateRecurringOffDayRequestObject,
) (api.CreateRecurringOffDayResponseObject, error) {
	return nil, httpx.NotImplemented()
}

func (s *Server) DeleteRecurringOffDay(
	ctx context.Context,
	r api.DeleteRecurringOffDayRequestObject,
) (api.DeleteRecurringOffDayResponseObject, error) {
	return nil, httpx.NotImplemented()
}

func (s *Server) UpdateRecurringOffDay(
	ctx context.Context,
	r api.UpdateRecurringOffDayRequestObject,
) (api.UpdateRecurringOffDayResponseObject, error) {
	return nil, httpx.NotImplemented()
}

func (s *Server) GetExemptions(
	ctx context.Context,
	r api.GetExemptionsRequestObject,
) (api.GetExemptionsResponseObject, error) {
	return nil, httpx.NotImplemented()
}

func (s *Server) CreateExemption(
	ctx context.Context,
	r api.CreateExemptionRequestObject,
) (api.CreateExemptionResponseObject, error) {
	return nil, httpx.NotImplemented()
}

func (s *Server) DeleteExemption(
	ctx context.Context,
	r api.DeleteExemptionRequestObject,
) (api.DeleteExemptionResponseObject, error) {
	return nil, httpx.NotImplemented()
}
