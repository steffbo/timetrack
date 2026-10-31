package server

import (
	"context"

	"github.com/steffbo/timetrack/backend-go/internal/api"
	"github.com/steffbo/timetrack/backend-go/internal/auth"
	"github.com/steffbo/timetrack/backend-go/internal/workinghours"
)

func (s *Server) workingHoursService() workinghours.Service {
	return workinghours.Service{
		Repository: workinghours.Repository{Q: s.Pool, Clock: s.Clock},
	}
}

func (s *Server) GetWorkingHours(
	ctx context.Context,
	_ api.GetWorkingHoursRequestObject,
) (api.GetWorkingHoursResponseObject, error) {
	p, _ := auth.Current(ctx)
	v, e := s.workingHoursService().Get(ctx, p.ID)
	return api.GetWorkingHours200JSONResponse(v), e
}

func (s *Server) UpdateWorkingHours(
	ctx context.Context,
	r api.UpdateWorkingHoursRequestObject,
) (api.UpdateWorkingHoursResponseObject, error) {
	p, _ := auth.Current(ctx)
	v, e := s.workingHoursService().Update(ctx, p.ID, *r.Body)
	return api.UpdateWorkingHours200JSONResponse(v), e
}

func (s *Server) UpdateWorkingDay(
	ctx context.Context,
	r api.UpdateWorkingDayRequestObject,
) (api.UpdateWorkingDayResponseObject, error) {
	p, _ := auth.Current(ctx)
	v, e := s.workingHoursService().UpdateDay(ctx, p.ID, r.Weekday, *r.Body)
	return api.UpdateWorkingDay200JSONResponse(v), e
}

func (s *Server) GetWorkingHoursByUserId(
	ctx context.Context,
	r api.GetWorkingHoursByUserIdRequestObject,
) (api.GetWorkingHoursByUserIdResponseObject, error) {
	p, _ := auth.Current(ctx)
	v, e := s.workingHoursService().GetForUser(ctx, p.Role, r.UserId)
	return api.GetWorkingHoursByUserId200JSONResponse(v), e
}
