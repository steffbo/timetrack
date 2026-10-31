package users

import (
	"context"
	"strings"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/steffbo/timetrack/backend-go/internal/api"
	"github.com/steffbo/timetrack/backend-go/internal/auth"
	"github.com/steffbo/timetrack/backend-go/internal/clock"
	"github.com/steffbo/timetrack/backend-go/internal/db"
	"github.com/steffbo/timetrack/backend-go/internal/domain"
	"github.com/steffbo/timetrack/backend-go/internal/httpx"
	"golang.org/x/crypto/bcrypt"
)

type Service struct {
	Pool           *pgxpool.Pool
	Clock          clock.Clock
	Auth           *auth.Service
	UserRepository *Repository
}

func (s Service) repository() Repository {
	if s.UserRepository != nil {
		return *s.UserRepository
	}
	return Repository{Q: s.Pool, Clock: s.Clock}
}

func (s Service) List(ctx context.Context) ([]api.UserResponse, error) {
	return (Repository{s.Pool, s.Clock}).All(ctx)
}
func (s Service) Get(ctx context.Context, id int64) (api.UserResponse, error) {
	return (Repository{s.Pool, s.Clock}).Get(ctx, id)
}

func (s Service) GetFor(
	ctx context.Context,
	id int64,
	principal auth.Principal,
) (api.UserResponse, error) {
	user, err := s.Get(ctx, id)
	if err != nil {
		return api.UserResponse{}, err
	}
	if principal.Role != domain.Admin && principal.ID != id {
		return api.UserResponse{}, httpx.Forbidden("Keine Berechtigung für diese Aktion")
	}
	return user, nil
}

func (s Service) GetRequired(ctx context.Context, id *int64) (User, error) {
	if id == nil {
		return User{}, httpx.InvalidArgument("User ID must not be null")
	}
	user, found, err := s.repository().FindByID(ctx, *id)
	if err != nil {
		return User{}, err
	}
	if !found {
		return User{}, httpx.NotFound("USER_NOT_FOUND", "Benutzer nicht gefunden")
	}
	return user, nil
}
func (s Service) Create(
	ctx context.Context,
	in api.CreateUserRequest,
) (out api.UserResponse, err error) {
	var hash *string
	if in.Password != nil && strings.TrimSpace(*in.Password) != "" {
		v, e := bcrypt.GenerateFromPassword([]byte(*in.Password), 10)
		if e != nil {
			return out, e
		}
		x := string(v)
		hash = &x
	}
	err = db.Transaction(ctx, s.Pool, func(tx pgx.Tx) error {
		var e error
		out, e = (Repository{tx, s.Clock}).Create(ctx, tx, in, hash)
		if e != nil {
			return e
		}
		if hash == nil {
			invite, inviteErr := s.Auth.GenerateInviteWithQuerier(ctx, tx, out.Id)
			if inviteErr != nil {
				return inviteErr
			}
			out.InviteUrl = &invite.InviteUrl
		}
		return nil
	})
	return
}
func (s Service) Update(
	ctx context.Context,
	id int64,
	in api.UpdateUserRequest,
	p auth.Principal,
) (out api.UserResponse, err error) {
	var hash *string
	if in.Password != nil && strings.TrimSpace(*in.Password) != "" {
		v, e := bcrypt.GenerateFromPassword([]byte(*in.Password), 10)
		if e != nil {
			return out, e
		}
		x := string(v)
		hash = &x
	}
	err = db.Transaction(ctx, s.Pool, func(tx pgx.Tx) error {
		var e error
		out, e = (Repository{Q: tx, Clock: s.Clock}).Update(ctx, id, in, p, hash)
		return e
	})
	return
}

func (s Service) Delete(ctx context.Context, id int64) error {
	return db.Transaction(ctx, s.Pool, func(tx pgx.Tx) error {
		return (Repository{Q: tx, Clock: s.Clock}).Delete(ctx, id)
	})
}
func (s Service) Invite(ctx context.Context, id int64) (api.InviteTokenResponse, error) {
	return s.Auth.GenerateInvite(ctx, id)
}

var _ db.Querier = (*pgxpool.Pool)(nil)
