package auth

import (
	"context"
	"errors"
	"fmt"
	"strconv"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/oapi-codegen/runtime/types"
	"github.com/steffbo/timetrack/backend/internal/api"
	"github.com/steffbo/timetrack/backend/internal/clock"
	"github.com/steffbo/timetrack/backend/internal/db"
	"github.com/steffbo/timetrack/backend/internal/domain"
	"github.com/steffbo/timetrack/backend/internal/httpx"
	"golang.org/x/crypto/bcrypt"
)

type Service struct {
	Pool            *pgxpool.Pool
	Tokens          Tokens
	Clock           clock.Clock
	FrontendBaseURL string
}
type UserRecord struct {
	Principal
	PasswordHash        *string
	FirstName, LastName string
}

func FindUser(ctx context.Context, q db.Querier, where string, value any) (UserRecord, error) {
	var u UserRecord
	err := q.QueryRow(ctx, `SELECT id,email,role,active,password_hash,first_name,last_name FROM users WHERE `+where, value).
		Scan(&u.ID, &u.Email, &u.Role, &u.Active, &u.PasswordHash, &u.FirstName, &u.LastName)
	return u, err
}
func (s *Service) ByEmail(ctx context.Context, email string) (Principal, error) {
	u, err := FindUser(ctx, s.Pool, "email=$1", email)
	return u.Principal, err
}
func (s *Service) response(p Principal, access, refresh string) api.AuthResponse {
	disabled := false
	return api.AuthResponse{AccessToken: access, RefreshToken: refresh, TokenType: "Bearer",
		ExpiresIn: int64(s.Tokens.AccessExpiration / time.Second), User: &api.UserResponse{
			Id: p.ID, Email: types.Email(p.Email), Role: api.UserResponseRole(p.Role), Active: p.Active,
			HalfDayHolidaysEnabled: &disabled}}
}
func (s *Service) issue(ctx context.Context, q db.Querier, p Principal) (api.AuthResponse, error) {
	access, err := s.Tokens.Generate(p, s.Tokens.AccessExpiration)
	if err != nil {
		return api.AuthResponse{}, err
	}
	refresh, err := s.Tokens.Generate(p, s.Tokens.RefreshExpiration)
	if err != nil {
		return api.AuthResponse{}, err
	}
	expiry := clock.LocalTimestamp(s.Clock.Now()).Add(s.Tokens.RefreshExpiration.Truncate(time.Second))
	_, err = q.Exec(ctx, `INSERT INTO refresh_tokens(user_id,token,expires_at)VALUES($1,$2,$3)`, p.ID, refresh, expiry)
	return s.response(p, access, refresh), err
}
func (s *Service) Login(ctx context.Context, r api.LoginRequest) (out api.AuthResponse, err error) {
	err = db.Transaction(ctx, s.Pool, func(tx pgx.Tx) error {
		u, e := FindUser(ctx, tx, "email=$1", string(r.Email))
		if e != nil && !errors.Is(e, pgx.ErrNoRows) {
			return e
		}
		if errors.Is(e, pgx.ErrNoRows) {
			return httpx.New(401, "INVALID_CREDENTIALS", "Ungültige Anmeldedaten")
		}
		// DaoAuthenticationProvider checks account state before password matching.
		if !u.Active {
			message := "User is disabled"
			if httpx.IsGerman(ctx) {
				message = "Der Benutzer ist deaktiviert"
			}
			return fmt.Errorf("%s", message)
		}
		if u.PasswordHash == nil || bcrypt.CompareHashAndPassword([]byte(*u.PasswordHash), []byte(r.Password)) != nil {
			return httpx.New(401, "INVALID_CREDENTIALS", "Ungültige Anmeldedaten")
		}
		// Password authentication adds this authority to the authenticated principal.
		u.Authorities = "ROLE_" + string(u.Role) + ",FACTOR_PASSWORD"
		out, e = s.issue(ctx, tx, u.Principal)
		return e
	})
	return
}
func (s *Service) Refresh(ctx context.Context, raw string) (out api.AuthResponse, err error) {
	claims, e := s.Tokens.Parse(raw)
	if e != nil {
		return out, httpx.InvalidArgument("Ungültiges Refresh-Token")
	}
	err = db.Transaction(ctx, s.Pool, func(tx pgx.Tx) error {
		var expiry time.Time
		e := tx.QueryRow(ctx, `SELECT expires_at FROM refresh_tokens WHERE token=$1`, raw).Scan(&expiry)
		if errors.Is(e, pgx.ErrNoRows) {
			return httpx.InvalidArgument("Refresh-Token nicht gefunden")
		}
		if e != nil {
			return e
		}
		if clock.LocalTimestamp(s.Clock.Now()).After(expiry) {
			return httpx.InvalidArgument("Refresh-Token ist abgelaufen")
		}
		id, _ := strconv.ParseInt(claims.Subject, 10, 64)
		u, e := FindUser(ctx, tx, "id=$1", id)
		if errors.Is(e, pgx.ErrNoRows) {
			return httpx.InvalidArgument("Benutzer nicht gefunden")
		}
		if e != nil {
			return e
		}
		if !u.Active {
			return httpx.InvalidArgument("Benutzerkonto ist inaktiv")
		}
		access, e := s.Tokens.Generate(u.Principal, s.Tokens.AccessExpiration)
		out = s.response(u.Principal, access, raw)
		return e
	})
	return
}
func (s *Service) Logout(ctx context.Context) error {
	p, ok := Current(ctx)
	if !ok {
		return errors.New("logout requires an authenticated user")
	}
	_, err := s.Pool.Exec(ctx, `DELETE FROM refresh_tokens WHERE user_id=$1`, p.ID)
	return err
}
func (s *Service) invite(ctx context.Context, q db.Querier, token string, lock bool) (UserRecord, error) {
	var id int64
	var expiry time.Time
	sql := `SELECT user_id,expires_at FROM invite_tokens WHERE token=$1`
	if lock {
		sql += " FOR UPDATE"
	}
	err := q.QueryRow(ctx, sql, token).Scan(&id, &expiry)
	if errors.Is(err, pgx.ErrNoRows) {
		return UserRecord{}, httpx.NotFound("INVITE_TOKEN_NOT_FOUND", "Einladungslink nicht gefunden")
	}
	if err != nil {
		return UserRecord{}, err
	}
	if clock.LocalTimestamp(s.Clock.Now()).After(expiry) {
		return UserRecord{}, httpx.New(410, "INVITE_TOKEN_EXPIRED", "Einladungslink ist abgelaufen")
	}
	return FindUser(ctx, q, "id=$1", id)
}
func (s *Service) InviteInfo(ctx context.Context, token string) (api.InviteInfoResponse, error) {
	u, err := s.invite(ctx, s.Pool, token, false)
	return api.InviteInfoResponse{Email: types.Email(u.Email), FirstName: u.FirstName, LastName: u.LastName,
		IsPasswordReset: u.PasswordHash != nil}, err
}
func (s *Service) Register(ctx context.Context, r api.RegisterWithInviteRequest) (out api.AuthResponse, err error) {
	err = db.Transaction(ctx, s.Pool, func(tx pgx.Tx) error {
		u, e := s.invite(ctx, tx, r.Token, true)
		if e != nil {
			return e
		}
		hash, e := bcrypt.GenerateFromPassword([]byte(r.Password), 10)
		if e != nil {
			return e
		}
		_, e = tx.Exec(ctx, `UPDATE users SET password_hash=$1,first_name=$2,last_name=$3,active=true,
   updated_at=$4 WHERE id=$5`, string(hash), r.FirstName, r.LastName, clock.LocalTimestamp(s.Clock.Now()), u.ID)
		if e != nil {
			return e
		}
		if _, e = tx.Exec(ctx, `DELETE FROM invite_tokens WHERE token=$1`, r.Token); e != nil {
			return e
		}
		u.Active = true
		out, e = s.issue(ctx, tx, u.Principal)
		return e
	})
	return
}

// GenerateInvite issues an invite in its own transaction.
func (s *Service) GenerateInvite(ctx context.Context, id int64) (out api.InviteTokenResponse, err error) {
	err = db.Transaction(ctx, s.Pool, func(tx pgx.Tx) error {
		var e error
		out, e = s.GenerateInviteWithQuerier(ctx, tx, id)
		return e
	})
	return
}

// GenerateInviteWithQuerier issues an invite using the caller's transaction.
func (s *Service) GenerateInviteWithQuerier(
	ctx context.Context,
	q db.Querier,
	id int64,
) (api.InviteTokenResponse, error) {
	if _, err := FindUser(ctx, q, "id=$1", id); err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return api.InviteTokenResponse{}, httpx.NotFound(
				"USER_NOT_FOUND",
				fmt.Sprintf("Benutzer nicht gefunden: %d", id),
			)
		}
		return api.InviteTokenResponse{}, err
	}
	if _, err := q.Exec(ctx, `DELETE FROM invite_tokens WHERE user_id=$1`, id); err != nil {
		return api.InviteTokenResponse{}, err
	}
	token := uuid.NewString()
	expiry := clock.LocalTimestamp(s.Clock.Now()).AddDate(0, 0, 30)
	_, err := q.Exec(
		ctx,
		`INSERT INTO invite_tokens(user_id,token,expires_at) VALUES($1,$2,$3)`,
		id,
		token,
		expiry,
	)
	return api.InviteTokenResponse{
		InviteUrl: s.FrontendBaseURL + "/register/" + token,
		ExpiresAt: expiry,
	}, err
}

func (s *Service) Impersonate(ctx context.Context, id int64) (out api.AuthResponse, err error) {
	admin, ok := Current(ctx)
	if !ok || admin.Role != domain.Admin {
		return out, httpx.Forbidden("Nur Administratoren können andere Benutzer verkörpern")
	}
	err = db.Transaction(ctx, s.Pool, func(tx pgx.Tx) error {
		u, e := FindUser(ctx, tx, "id=$1", id)
		if errors.Is(e, pgx.ErrNoRows) {
			return httpx.NotFound("USER_NOT_FOUND", "Benutzer nicht gefunden")
		}
		if e != nil {
			return e
		}
		if u.Role == domain.Admin {
			return httpx.Forbidden("Administratoren können nicht andere Administratoren verkörpern")
		}
		u.ImpersonatedBy = &admin.ID
		out, e = s.issue(ctx, tx, u.Principal)
		return e
	})
	return
}
