package server

import (
	"context"

	"github.com/steffbo/timetrack/backend-go/internal/api"
	"github.com/steffbo/timetrack/backend-go/internal/httpx"
)

func (s *Server) GetAllUsers(
	ctx context.Context,
	r api.GetAllUsersRequestObject,
) (api.GetAllUsersResponseObject, error) {
	return nil, httpx.NotImplemented()
}

func (s *Server) CreateUser(
	ctx context.Context,
	r api.CreateUserRequestObject,
) (api.CreateUserResponseObject, error) {
	return nil, httpx.NotImplemented()
}

func (s *Server) GetCurrentUser(
	ctx context.Context,
	r api.GetCurrentUserRequestObject,
) (api.GetCurrentUserResponseObject, error) {
	return nil, httpx.NotImplemented()
}

func (s *Server) DeleteUser(
	ctx context.Context,
	r api.DeleteUserRequestObject,
) (api.DeleteUserResponseObject, error) {
	return nil, httpx.NotImplemented()
}

func (s *Server) GetUserById(
	ctx context.Context,
	r api.GetUserByIdRequestObject,
) (api.GetUserByIdResponseObject, error) {
	return nil, httpx.NotImplemented()
}

func (s *Server) UpdateUser(
	ctx context.Context,
	r api.UpdateUserRequestObject,
) (api.UpdateUserResponseObject, error) {
	return nil, httpx.NotImplemented()
}

func (s *Server) ImpersonateUser(
	ctx context.Context,
	r api.ImpersonateUserRequestObject,
) (api.ImpersonateUserResponseObject, error) {
	return nil, httpx.NotImplemented()
}

func (s *Server) GenerateInviteToken(
	ctx context.Context,
	r api.GenerateInviteTokenRequestObject,
) (api.GenerateInviteTokenResponseObject, error) {
	return nil, httpx.NotImplemented()
}
