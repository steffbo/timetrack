package server

import (
	"context"

	"github.com/steffbo/timetrack/backend-go/internal/api"
	"github.com/steffbo/timetrack/backend-go/internal/httpx"
)

func (s *Server) GetTimeOffEntries(
	ctx context.Context,
	r api.GetTimeOffEntriesRequestObject,
) (api.GetTimeOffEntriesResponseObject, error) {
	return nil, httpx.NotImplemented()
}

func (s *Server) CreateTimeOff(
	ctx context.Context,
	r api.CreateTimeOffRequestObject,
) (api.CreateTimeOffResponseObject, error) {
	return nil, httpx.NotImplemented()
}

func (s *Server) DeleteTimeOff(
	ctx context.Context,
	r api.DeleteTimeOffRequestObject,
) (api.DeleteTimeOffResponseObject, error) {
	return nil, httpx.NotImplemented()
}

func (s *Server) UpdateTimeOff(
	ctx context.Context,
	r api.UpdateTimeOffRequestObject,
) (api.UpdateTimeOffResponseObject, error) {
	return nil, httpx.NotImplemented()
}
