package users

import (
	"context"
	"errors"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
	"github.com/oapi-codegen/runtime/types"
	"github.com/steffbo/timetrack/backend-go/internal/api"
	"github.com/steffbo/timetrack/backend-go/internal/auth"
	"github.com/steffbo/timetrack/backend-go/internal/clock"
	"github.com/steffbo/timetrack/backend-go/internal/db"
	"github.com/steffbo/timetrack/backend-go/internal/domain"
	"github.com/steffbo/timetrack/backend-go/internal/httpx"
)

type Repository struct {
	Q     db.Querier
	Clock clock.Clock
}

type User struct {
	ID                     int64
	Email                  string
	PasswordHash           *string
	FirstName              string
	LastName               string
	Role                   domain.Role
	Active                 bool
	State                  domain.GermanState
	HalfDayHolidaysEnabled bool
	CreatedAt              time.Time
	UpdatedAt              time.Time
}

func (u User) FullName() string { return u.FirstName + " " + u.LastName }
func (u User) IsAdmin() bool    { return u.Role == domain.Admin }

func (r Repository) Save(ctx context.Context, user User) (User, error) {
	if user.ID == 0 {
		err := r.Q.QueryRow(
			ctx,
			`INSERT INTO users(
email,password_hash,first_name,last_name,role,active,state,half_day_holidays_enabled
) VALUES($1,$2,$3,$4,$5,$6,$7,$8) RETURNING id`,
			user.Email,
			user.PasswordHash,
			user.FirstName,
			user.LastName,
			user.Role,
			user.Active,
			user.State,
			user.HalfDayHolidaysEnabled,
		).Scan(&user.ID)
		if err != nil {
			return User{}, err
		}
	} else {
		_, err := r.Q.Exec(
			ctx,
			`UPDATE users SET email=$1,password_hash=$2,first_name=$3,last_name=$4,
role=$5,active=$6,state=$7,half_day_holidays_enabled=$8,updated_at=$9 WHERE id=$10`,
			user.Email,
			user.PasswordHash,
			user.FirstName,
			user.LastName,
			user.Role,
			user.Active,
			user.State,
			user.HalfDayHolidaysEnabled,
			clock.LocalTimestamp(r.Clock.Now()),
			user.ID,
		)
		if err != nil {
			return User{}, err
		}
	}
	found, _, err := r.FindByID(ctx, user.ID)
	return found, err
}

func (r Repository) FindByID(ctx context.Context, id int64) (User, bool, error) {
	return r.find(ctx, "id=$1", id)
}

func (r Repository) FindByEmail(ctx context.Context, email string) (User, bool, error) {
	return r.find(ctx, "email=$1", email)
}

func (r Repository) find(ctx context.Context, predicate string, value any) (User, bool, error) {
	var user User
	err := r.Q.QueryRow(
		ctx,
		`SELECT id,email,password_hash,first_name,last_name,role,active,state,
half_day_holidays_enabled,created_at,updated_at FROM users WHERE `+predicate,
		value,
	).Scan(
		&user.ID,
		&user.Email,
		&user.PasswordHash,
		&user.FirstName,
		&user.LastName,
		&user.Role,
		&user.Active,
		&user.State,
		&user.HalfDayHolidaysEnabled,
		&user.CreatedAt,
		&user.UpdatedAt,
	)
	if errors.Is(err, pgx.ErrNoRows) {
		return User{}, false, nil
	}
	return user, err == nil, err
}

func (r Repository) ExistsByEmail(ctx context.Context, email string) (bool, error) {
	var exists bool
	err := r.Q.QueryRow(
		ctx,
		`SELECT EXISTS(SELECT 1 FROM users WHERE email=$1)`,
		email,
	).Scan(&exists)
	return exists, err
}

func (r Repository) DeleteByID(ctx context.Context, id int64) error {
	_, err := r.Q.Exec(ctx, `DELETE FROM users WHERE id=$1`, id)
	return err
}

type record struct {
	id                    int64
	email, role           string
	active                bool
	password, first, last *string
	state                 *string
	half                  bool
	created, updated      any
}

func (r Repository) get(ctx context.Context, id int64) (record, error) {
	var u record
	e := r.Q.QueryRow(
		ctx,
		`SELECT id,email,role,active,password_hash,first_name,last_name,state,
half_day_holidays_enabled,created_at,updated_at FROM users WHERE id=$1`,
		id,
	).Scan(
		&u.id,
		&u.email,
		&u.role,
		&u.active,
		&u.password,
		&u.first,
		&u.last,
		&u.state,
		&u.half,
		&u.created,
		&u.updated,
	)
	if errors.Is(e, pgx.ErrNoRows) {
		return u, httpx.NotFound("USER_NOT_FOUND", "Benutzer nicht gefunden")
	}
	return u, e
}

func response(u record) api.UserResponse {
	var created, updated = datePtr(u.created), datePtr(u.updated)
	return api.UserResponse{
		Id:                     u.id,
		Email:                  types.Email(u.email),
		Role:                   api.UserResponseRole(u.role),
		Active:                 u.active,
		FirstName:              u.first,
		LastName:               u.last,
		State:                  statePtr(u.state),
		HalfDayHolidaysEnabled: boolPtr(u.half),
		Pending:                boolPtr(u.password == nil),
		CreatedAt:              created,
		UpdatedAt:              updated,
	}
}

func datePtr(v any) *time.Time {
	if t, ok := v.(time.Time); ok {
		return &t
	}
	return nil
}

func statePtr(v *string) *api.UserResponseState {
	if v == nil {
		return nil
	}
	x := api.UserResponseState(*v)
	return &x
}

func boolPtr(v bool) *bool { return &v }

func (r Repository) All(ctx context.Context) ([]api.UserResponse, error) {
	rows, e := r.Q.Query(ctx, `SELECT id,email,role,active,password_hash,first_name,last_name,state,
half_day_holidays_enabled,created_at,updated_at FROM users ORDER BY id`)
	if e != nil {
		return nil, e
	}
	defer rows.Close()
	out := []api.UserResponse{}
	for rows.Next() {
		var u record
		e = rows.Scan(
			&u.id,
			&u.email,
			&u.role,
			&u.active,
			&u.password,
			&u.first,
			&u.last,
			&u.state,
			&u.half,
			&u.created,
			&u.updated,
		)
		if e != nil {
			return nil, e
		}
		out = append(out, response(u))
	}
	return out, rows.Err()
}

func (r Repository) Get(ctx context.Context, id int64) (api.UserResponse, error) {
	u, e := r.get(ctx, id)
	if e != nil {
		return api.UserResponse{}, e
	}
	return response(u), nil
}

func (r Repository) Create(
	ctx context.Context,
	q db.Querier,
	in api.CreateUserRequest,
	hash *string,
) (api.UserResponse, error) {
	state := "BERLIN"
	if in.State != nil {
		state = string(*in.State)
	}
	active := true
	if in.Active != nil {
		active = *in.Active
	}
	half := false
	if in.HalfDayHolidaysEnabled != nil {
		half = *in.HalfDayHolidaysEnabled
	}
	var id int64
	err := q.QueryRow(
		ctx,
		`INSERT INTO users(
email,password_hash,first_name,last_name,role,active,state,half_day_holidays_enabled
) VALUES($1,$2,$3,$4,$5,$6,$7,$8) RETURNING id`,
		string(in.Email),
		hash,
		in.FirstName,
		in.LastName,
		string(in.Role),
		active,
		state,
		half,
	).Scan(&id)
	if err != nil {
		if isUnique(err) {
			return api.UserResponse{}, httpx.Conflict("DUPLICATE_EMAIL", "Email bereits vergeben: "+string(in.Email))
		}
		return api.UserResponse{}, err
	}
	for day := 1; day <= 7; day++ {
		hours := 8.0
		working := true
		if day >= 6 {
			hours = 0
			working = false
		}
		_, err = q.Exec(
			ctx,
			`INSERT INTO working_hours(user_id,weekday,hours,is_working_day)
VALUES($1,$2,$3,$4)`,
			id,
			day,
			hours,
			working,
		)
		if err != nil {
			return api.UserResponse{}, err
		}
	}
	u, err := r.get(ctx, id)
	if err != nil {
		return api.UserResponse{}, err
	}
	return response(u), nil
}

func (r Repository) Update(
	ctx context.Context,
	id int64,
	in api.UpdateUserRequest,
	principal auth.Principal,
	hash *string,
) (api.UserResponse, error) {
	u, e := r.get(ctx, id)
	if e != nil {
		return api.UserResponse{}, e
	}
	admin := principal.Role == domain.Admin
	if !admin && principal.ID != id {
		return api.UserResponse{}, httpx.Forbidden("Keine Berechtigung für diese Aktion")
	}
	if !admin && (in.Role != nil || in.Active != nil) {
		return api.UserResponse{}, httpx.Forbidden("Keine Berechtigung zum Ändern von Rolle oder Status")
	}
	email := u.email
	if in.Email != nil && string(*in.Email) != email {
		var exists bool
		e = r.Q.QueryRow(
			ctx,
			`SELECT EXISTS(SELECT 1 FROM users WHERE email=$1 AND id<>$2)`,
			string(*in.Email),
			id,
		).Scan(&exists)
		if e != nil {
			return api.UserResponse{}, e
		}
		if exists {
			return api.UserResponse{}, httpx.Conflict("DUPLICATE_EMAIL", "Email bereits vergeben: "+string(*in.Email))
		}
		email = string(*in.Email)
	}
	first, last := u.first, u.last
	if in.FirstName != nil {
		first = in.FirstName
	}
	if in.LastName != nil {
		last = in.LastName
	}
	role, active, state, half := u.role, u.active, "BERLIN", u.half
	if u.state != nil {
		state = *u.state
	}
	if admin && in.Role != nil {
		role = string(*in.Role)
	}
	if admin && in.Active != nil {
		active = *in.Active
	}
	if in.State != nil {
		state = string(*in.State)
	}
	if in.HalfDayHolidaysEnabled != nil {
		half = *in.HalfDayHolidaysEnabled
	}
	pass := u.password
	if hash != nil {
		pass = hash
	}
	_, e = r.Q.Exec(
		ctx,
		`UPDATE users SET email=$1,password_hash=$2,first_name=$3,last_name=$4,
role=$5,active=$6,state=$7,half_day_holidays_enabled=$8,updated_at=$9 WHERE id=$10`,
		email,
		pass,
		first,
		last,
		role,
		active,
		state,
		half,
		clock.LocalTimestamp(r.Clock.Now()),
		id,
	)
	if e != nil {
		return api.UserResponse{}, e
	}
	return r.Get(ctx, id)
}

func (r Repository) Delete(ctx context.Context, id int64) error {
	u, e := r.get(ctx, id)
	if e != nil {
		return e
	}
	_, e = r.Q.Exec(ctx, `DELETE FROM users WHERE id=$1`, u.id)
	return e
}

func isUnique(e error) bool {
	var pg *pgconn.PgError
	return errors.As(e, &pg) && pg.Code == "23505"
}
