package server

import (
	"context"

	"github.com/steffbo/timetrack/backend-go/internal/api"
	"github.com/steffbo/timetrack/backend-go/internal/httpx"
)

func (s *Server) GetVacationBalance(
	ctx context.Context,
	r api.GetVacationBalanceRequestObject,
) (api.GetVacationBalanceResponseObject, error) {
	return nil, httpx.NotImplemented()
}

func (s *Server) UpdateVacationBalance(
	ctx context.Context,
	r api.UpdateVacationBalanceRequestObject,
) (api.UpdateVacationBalanceResponseObject, error) {
	return nil, httpx.NotImplemented()
}
