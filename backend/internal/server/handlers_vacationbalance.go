package server

import (
	"context"

	"github.com/steffbo/timetrack/backend/internal/api"
	"github.com/steffbo/timetrack/backend/internal/auth"
	"github.com/steffbo/timetrack/backend/internal/vacationbalance"
)

func (s *Server) GetVacationBalance(
	ctx context.Context,
	r api.GetVacationBalanceRequestObject,
) (api.GetVacationBalanceResponseObject, error) {
	p, _ := auth.Current(ctx)
	year := s.Clock.Now().Year()
	if r.Params.Year != nil {
		year = *r.Params.Year
	}
	v, e := (vacationbalance.Repository{Pool: s.Pool, Clock: s.Clock}).Balance(ctx, p.ID, year)
	return api.GetVacationBalance200JSONResponse(v), e
}

func (s *Server) UpdateVacationBalance(
	ctx context.Context,
	r api.UpdateVacationBalanceRequestObject,
) (api.UpdateVacationBalanceResponseObject, error) {
	p, _ := auth.Current(ctx)
	v, e := (vacationbalance.Repository{Pool: s.Pool, Clock: s.Clock}).Update(ctx, p, *r.Body)
	return api.UpdateVacationBalance200JSONResponse(v), e
}
