package server

import (
	"context"

	"github.com/steffbo/timetrack/backend-go/internal/api"
	"github.com/steffbo/timetrack/backend-go/internal/auth"
	"github.com/steffbo/timetrack/backend-go/internal/httpx"
	"github.com/steffbo/timetrack/backend-go/internal/timeoff"
)

func (s *Server) timeOff() timeoff.Service { return timeoff.Service{Pool: s.Pool, Clock: s.Clock} }

func (s *Server) GetTimeOffEntries(
	ctx context.Context,
	r api.GetTimeOffEntriesRequestObject,
) (api.GetTimeOffEntriesResponseObject, error) {
	principal, _ := auth.Current(ctx)
	out, err := s.timeOff().List(ctx, principal.ID, r.Params.StartDate, r.Params.EndDate)
	if err != nil {
		return nil, err
	}
	return api.GetTimeOffEntries200JSONResponse(out), nil
}

func (s *Server) CreateTimeOff(
	ctx context.Context,
	r api.CreateTimeOffRequestObject,
) (api.CreateTimeOffResponseObject, error) {
	if r.Body == nil {
		return nil, httpx.InvalidArgument("Request body is missing")
	}
	principal, _ := auth.Current(ctx)
	out, err := s.timeOff().Create(ctx, principal.ID, *r.Body)
	if err != nil {
		return nil, err
	}
	return api.CreateTimeOff201JSONResponse(out), nil
}

func (s *Server) DeleteTimeOff(
	ctx context.Context,
	r api.DeleteTimeOffRequestObject,
) (api.DeleteTimeOffResponseObject, error) {
	principal, _ := auth.Current(ctx)
	if err := s.timeOff().Delete(ctx, principal.ID, r.Id); err != nil {
		return nil, err
	}
	return api.DeleteTimeOff204Response{}, nil
}

func (s *Server) UpdateTimeOff(
	ctx context.Context,
	r api.UpdateTimeOffRequestObject,
) (api.UpdateTimeOffResponseObject, error) {
	if r.Body == nil {
		return nil, httpx.InvalidArgument("Request body is missing")
	}
	principal, _ := auth.Current(ctx)
	out, err := s.timeOff().Update(ctx, principal.ID, r.Id, *r.Body)
	if err != nil {
		return nil, err
	}
	return api.UpdateTimeOff200JSONResponse(out), nil
}
