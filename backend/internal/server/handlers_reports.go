package server

import (
	"bytes"
	"context"
	"fmt"

	"github.com/steffbo/timetrack/backend-go/internal/api"
	"github.com/steffbo/timetrack/backend-go/internal/auth"
	"github.com/steffbo/timetrack/backend-go/internal/reports"
)

func (s *Server) ExportMonthlyReport(ctx context.Context, r api.ExportMonthlyReportRequestObject) (
	api.ExportMonthlyReportResponseObject, error) {
	p, _ := auth.Current(ctx)
	pdf, err := (reports.Service{Pool: s.Pool}).PDF(ctx, p.ID, r.Params.Year, r.Params.Month)
	if err != nil {
		return nil, err
	}
	return api.ExportMonthlyReport200ApplicationpdfResponse{Body: bytes.NewReader(pdf),
		ContentLength: int64(len(pdf)), Headers: api.ExportMonthlyReport200ResponseHeaders{
			ContentDisposition: fmt.Sprintf(`attachment; filename="stundenzettel_%d_%02d.pdf"`,
				r.Params.Year, r.Params.Month),
		}}, nil
}
