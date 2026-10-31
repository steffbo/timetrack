package server

import (
	"context"

	"github.com/steffbo/timetrack/backend/internal/api"
	"github.com/steffbo/timetrack/backend/internal/auth"
	"github.com/steffbo/timetrack/backend/internal/users"
)

func (s *Server) GetAllUsers(
	ctx context.Context,
	_ api.GetAllUsersRequestObject,
) (api.GetAllUsersResponseObject, error) {
	v, e := (users.Service{Pool: s.Pool, Clock: s.Clock, Auth: s.Auth}).List(ctx)
	return api.GetAllUsers200JSONResponse(v), e
}

func (s *Server) CreateUser(
	ctx context.Context,
	r api.CreateUserRequestObject,
) (api.CreateUserResponseObject, error) {
	v, e := (users.Service{Pool: s.Pool, Clock: s.Clock, Auth: s.Auth}).Create(ctx, *r.Body)
	return api.CreateUser201JSONResponse(v), e
}

func (s *Server) GetCurrentUser(
	ctx context.Context,
	_ api.GetCurrentUserRequestObject,
) (api.GetCurrentUserResponseObject, error) {
	p, _ := auth.Current(ctx)
	v, e := (users.Service{Pool: s.Pool, Clock: s.Clock, Auth: s.Auth}).Get(ctx, p.ID)
	return api.GetCurrentUser200JSONResponse(v), e
}

func (s *Server) DeleteUser(
	ctx context.Context,
	r api.DeleteUserRequestObject,
) (api.DeleteUserResponseObject, error) {
	e := (users.Service{Pool: s.Pool, Clock: s.Clock, Auth: s.Auth}).Delete(ctx, r.Id)
	return api.DeleteUser204Response{}, e
}

func (s *Server) GetUserById(
	ctx context.Context,
	r api.GetUserByIdRequestObject,
) (api.GetUserByIdResponseObject, error) {
	p, _ := auth.Current(ctx)
	v, e := (users.Service{Pool: s.Pool, Clock: s.Clock, Auth: s.Auth}).GetFor(ctx, r.Id, p)
	return api.GetUserById200JSONResponse(v), e
}

func (s *Server) UpdateUser(
	ctx context.Context,
	r api.UpdateUserRequestObject,
) (api.UpdateUserResponseObject, error) {
	p, _ := auth.Current(ctx)
	v, e := (users.Service{Pool: s.Pool, Clock: s.Clock, Auth: s.Auth}).Update(
		ctx,
		r.Id,
		*r.Body,
		p,
	)
	return api.UpdateUser200JSONResponse(v), e
}

func (s *Server) ImpersonateUser(
	ctx context.Context,
	r api.ImpersonateUserRequestObject,
) (api.ImpersonateUserResponseObject, error) {
	v, e := s.Auth.Impersonate(ctx, r.Id)
	return api.ImpersonateUser200JSONResponse(v), e
}

func (s *Server) GenerateInviteToken(
	ctx context.Context,
	r api.GenerateInviteTokenRequestObject,
) (api.GenerateInviteTokenResponseObject, error) {
	v, e := (users.Service{Pool: s.Pool, Clock: s.Clock, Auth: s.Auth}).Invite(ctx, r.Id)
	return api.GenerateInviteToken200JSONResponse(v), e
}
