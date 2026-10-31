package server

import (
	"context"

	"github.com/steffbo/timetrack/backend-go/internal/api"
	"github.com/steffbo/timetrack/backend-go/internal/httpx"
)

func (s *Server) GetTimeEntries(
	ctx context.Context,
	r api.GetTimeEntriesRequestObject,
) (api.GetTimeEntriesResponseObject, error) {
	return nil, httpx.NotImplemented()
}

func (s *Server) CreateTimeEntry(
	ctx context.Context,
	r api.CreateTimeEntryRequestObject,
) (api.CreateTimeEntryResponseObject, error) {
	return nil, httpx.NotImplemented()
}

func (s *Server) ClockIn(ctx context.Context, r api.ClockInRequestObject) (api.ClockInResponseObject, error) {
	return nil, httpx.NotImplemented()
}

func (s *Server) ClockOut(
	ctx context.Context,
	r api.ClockOutRequestObject,
) (api.ClockOutResponseObject, error) {
	return nil, httpx.NotImplemented()
}

func (s *Server) GetDailySummary(
	ctx context.Context,
	r api.GetDailySummaryRequestObject,
) (api.GetDailySummaryResponseObject, error) {
	return nil, httpx.NotImplemented()
}

func (s *Server) DeleteTimeEntry(
	ctx context.Context,
	r api.DeleteTimeEntryRequestObject,
) (api.DeleteTimeEntryResponseObject, error) {
	return nil, httpx.NotImplemented()
}

func (s *Server) UpdateTimeEntry(
	ctx context.Context,
	r api.UpdateTimeEntryRequestObject,
) (api.UpdateTimeEntryResponseObject, error) {
	return nil, httpx.NotImplemented()
}
