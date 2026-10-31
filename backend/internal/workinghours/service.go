package workinghours

import (
	"context"

	"github.com/steffbo/timetrack/backend/internal/api"
	"github.com/steffbo/timetrack/backend/internal/domain"
	"github.com/steffbo/timetrack/backend/internal/httpx"
)

type Service struct{ Repository Repository }

func (s Service) Get(ctx context.Context, userID int64) (api.WorkingHoursResponse, error) {
	return s.Repository.Get(ctx, userID)
}

func (s Service) GetForUser(
	ctx context.Context,
	requestingRole domain.Role,
	targetUserID int64,
) (api.WorkingHoursResponse, error) {
	if requestingRole != domain.Admin {
		return api.WorkingHoursResponse{}, httpx.Forbidden(
			"Keine Berechtigung, Arbeitsstunden anderer Benutzer anzuzeigen",
		)
	}
	return s.Repository.Get(ctx, targetUserID)
}

func (s Service) Update(
	ctx context.Context,
	userID int64,
	request api.UpdateWorkingHoursRequest,
) (api.WorkingHoursResponse, error) {
	return s.Repository.Save(ctx, userID, request.WorkingDays)
}

func (s Service) UpdateDay(
	ctx context.Context,
	userID int64,
	weekday int,
	day api.UpdateWorkingDayConfig,
) (api.WorkingDayConfig, error) {
	day.Weekday = weekday
	return s.Repository.SaveDay(ctx, userID, day)
}
