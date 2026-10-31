package server

import (
	"context"

	"github.com/steffbo/timetrack/backend-go/internal/api"
	"github.com/steffbo/timetrack/backend-go/internal/httpx"
)

func (s *Server) ExportMonthlyReport(
	ctx context.Context,
	r api.ExportMonthlyReportRequestObject,
) (api.ExportMonthlyReportResponseObject, error) {
	return nil, httpx.NotImplemented()
}
