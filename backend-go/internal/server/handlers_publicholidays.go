package server

import (
	"context"

	"github.com/steffbo/timetrack/backend-go/internal/api"
	"github.com/steffbo/timetrack/backend-go/internal/httpx"
)

func (s *Server) GetPublicHolidays(
	ctx context.Context,
	r api.GetPublicHolidaysRequestObject,
) (api.GetPublicHolidaysResponseObject, error) {
	return nil, httpx.NotImplemented()
}
