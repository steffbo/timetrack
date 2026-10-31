package server

import (
	"context"

	"github.com/steffbo/timetrack/backend/internal/api"
	"github.com/steffbo/timetrack/backend/internal/publicholidays"
)

func (s *Server) GetPublicHolidays(ctx context.Context, r api.GetPublicHolidaysRequestObject) (
	api.GetPublicHolidaysResponseObject, error) {
	return api.GetPublicHolidays200JSONResponse(publicholidays.All()), nil
}
