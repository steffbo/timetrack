package server

import (
	"context"

	"github.com/steffbo/timetrack/backend-go/internal/api"
	"github.com/steffbo/timetrack/backend-go/internal/httpx"
)

func (s *Server) GetWorkingHours(
	ctx context.Context,
	r api.GetWorkingHoursRequestObject,
) (api.GetWorkingHoursResponseObject, error) {
	return nil, httpx.NotImplemented()
}

func (s *Server) UpdateWorkingHours(
	ctx context.Context,
	r api.UpdateWorkingHoursRequestObject,
) (api.UpdateWorkingHoursResponseObject, error) {
	return nil, httpx.NotImplemented()
}

func (s *Server) UpdateWorkingDay(
	ctx context.Context,
	r api.UpdateWorkingDayRequestObject,
) (api.UpdateWorkingDayResponseObject, error) {
	return nil, httpx.NotImplemented()
}

func (s *Server) GetWorkingHoursByUserId(
	ctx context.Context,
	r api.GetWorkingHoursByUserIdRequestObject,
) (api.GetWorkingHoursByUserIdResponseObject, error) {
	return nil, httpx.NotImplemented()
}
