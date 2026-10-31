package server

import (
	"context"

	"github.com/steffbo/timetrack/backend-go/internal/api"
	"github.com/steffbo/timetrack/backend-go/internal/publicholidays"
)

func (s *Server) GetPublicHolidays(ctx context.Context, r api.GetPublicHolidaysRequestObject) (
	api.GetPublicHolidaysResponseObject, error) {
	return api.GetPublicHolidays200JSONResponse(publicholidays.All()), nil
}
