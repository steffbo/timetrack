package server

import (
	"context"
	"time"

	"github.com/steffbo/timetrack/backend-go/internal/api"
	"github.com/steffbo/timetrack/backend-go/internal/auth"
	"github.com/steffbo/timetrack/backend-go/internal/timeentries"
)

func (s *Server) entriesService() timeentries.Service {
	return timeentries.Service{Pool: s.Pool, Clock: s.Clock}
}
func (s *Server) GetTimeEntries(ctx context.Context, r api.GetTimeEntriesRequestObject) (
	api.GetTimeEntriesResponseObject, error) {
	p, _ := auth.Current(ctx)
	var start, end *time.Time
	if r.Params.StartDate != nil {
		start = &r.Params.StartDate.Time
	}
	if r.Params.EndDate != nil {
		end = &r.Params.EndDate.Time
	}
	entries, err := s.entriesService().List(ctx, p.ID, start, end)
	if err != nil {
		return nil, err
	}
	out := []api.TimeEntryResponse{}
	for _, e := range entries {
		out = append(out, e.Response())
	}
	return api.GetTimeEntries200JSONResponse(out), nil
}
func (s *Server) CreateTimeEntry(ctx context.Context, r api.CreateTimeEntryRequestObject) (
	api.CreateTimeEntryResponseObject, error) {
	p, _ := auth.Current(ctx)
	e, err := s.entriesService().Create(ctx, p.ID, *r.Body)
	if err != nil {
		return nil, err
	}
	return api.CreateTimeEntry201JSONResponse(e.Response()), nil
}
func (s *Server) ClockIn(ctx context.Context, r api.ClockInRequestObject) (api.ClockInResponseObject, error) {
	p, _ := auth.Current(ctx)
	e, err := s.entriesService().ClockIn(ctx, p.ID, r.Body.Notes)
	if err != nil {
		return nil, err
	}
	return api.ClockIn201JSONResponse(e.Response()), nil
}
func (s *Server) ClockOut(ctx context.Context, r api.ClockOutRequestObject) (
	api.ClockOutResponseObject, error,
) {
	p, _ := auth.Current(ctx)
	e, err := s.entriesService().ClockOut(ctx, p.ID, r.Body.Notes)
	if err != nil {
		return nil, err
	}
	return api.ClockOut200JSONResponse(e.Response()), nil
}
func (s *Server) GetDailySummary(ctx context.Context, r api.GetDailySummaryRequestObject) (
	api.GetDailySummaryResponseObject, error) {
	p, _ := auth.Current(ctx)
	var start, end time.Time
	if r.Params.StartDate == nil || r.Params.EndDate == nil {
		now := s.Clock.Now()
		start = time.Date(now.Year(), now.Month(), 1, 0, 0, 0, 0, time.UTC)
		end = start.AddDate(0, 1, -1)
	} else {
		start, end = r.Params.StartDate.Time, r.Params.EndDate.Time
	}
	out, err := s.entriesService().Summary(ctx, p.ID, start, end)
	if err != nil {
		return nil, err
	}
	return api.GetDailySummary200JSONResponse(out), nil
}
func (s *Server) DeleteTimeEntry(ctx context.Context, r api.DeleteTimeEntryRequestObject) (
	api.DeleteTimeEntryResponseObject, error) {
	p, _ := auth.Current(ctx)
	if err := s.entriesService().Delete(ctx, p.ID, r.Id); err != nil {
		return nil, err
	}
	return api.DeleteTimeEntry204Response{}, nil
}
func (s *Server) UpdateTimeEntry(ctx context.Context, r api.UpdateTimeEntryRequestObject) (
	api.UpdateTimeEntryResponseObject, error) {
	p, _ := auth.Current(ctx)
	e, err := s.entriesService().Update(ctx, p.ID, r.Id, *r.Body)
	if err != nil {
		return nil, err
	}
	return api.UpdateTimeEntry200JSONResponse(e.Response()), nil
}
