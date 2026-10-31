package server

import (
	"context"

	"github.com/steffbo/timetrack/backend/internal/api"
)

func (s *Server) Login(ctx context.Context, r api.LoginRequestObject) (api.LoginResponseObject, error) {
	out, err := s.Auth.Login(ctx, *r.Body)
	return api.Login200JSONResponse(out), err
}
func (s *Server) RefreshToken(
	ctx context.Context,
	r api.RefreshTokenRequestObject,
) (api.RefreshTokenResponseObject, error) {
	out, err := s.Auth.Refresh(ctx, r.Body.RefreshToken)
	return api.RefreshToken200JSONResponse(out), err
}
func (s *Server) Logout(ctx context.Context, r api.LogoutRequestObject) (api.LogoutResponseObject, error) {
	return api.Logout204Response{}, s.Auth.Logout(ctx)
}
func (s *Server) GetInviteInfo(
	ctx context.Context,
	r api.GetInviteInfoRequestObject,
) (api.GetInviteInfoResponseObject, error) {
	out, err := s.Auth.InviteInfo(ctx, r.Token)
	return api.GetInviteInfo200JSONResponse(out), err
}
func (s *Server) RegisterWithInvite(
	ctx context.Context,
	r api.RegisterWithInviteRequestObject,
) (api.RegisterWithInviteResponseObject, error) {
	out, err := s.Auth.Register(ctx, *r.Body)
	return api.RegisterWithInvite200JSONResponse(out), err
}
