package integration_test

import (
	"context"
	"testing"

	"github.com/jackc/pgx/v5"
	"github.com/steffbo/timetrack/backend/internal/db"
	"github.com/steffbo/timetrack/backend/internal/domain"
	"github.com/steffbo/timetrack/backend/internal/users"
)

type countingUserQuerier struct {
	db.Querier
	queryRowCalls int
}

func (q *countingUserQuerier) QueryRow(
	ctx context.Context,
	sql string,
	args ...any,
) pgx.Row {
	q.queryRowCalls++
	return q.Querier.QueryRow(ctx, sql, args...)
}

func userServiceWithQueryCount(fixture packageBFixture) (users.Service, *countingUserQuerier) {
	querier := &countingUserQuerier{Querier: harness.Pool}
	repository := users.Repository{Q: querier, Clock: packageBClock}
	service := fixture.users
	service.UserRepository = &repository
	return service, querier
}

func TestUserServiceGetUserOrThrowReturnsUserWhenExists(t *testing.T) {
	fixture := newPackageBFixture(t)
	user := saveTestUser(t, fixture, "test@example.com", "Test", "User", domain.User)
	service, querier := userServiceWithQueryCount(fixture)
	result := mustValue(service.GetRequired(context.Background(), &user.ID))
	if result.ID != user.ID || result.Email != "test@example.com" ||
		result.FirstName != "Test" || result.LastName != "User" {
		t.Fatalf("result = %+v", result)
	}
	if querier.queryRowCalls != 1 {
		t.Fatalf("repository calls = %d, want 1", querier.queryRowCalls)
	}
}

func TestUserServiceGetUserOrThrowThrowsWhenUserNotFound(t *testing.T) {
	fixture := newPackageBFixture(t)
	service, querier := userServiceWithQueryCount(fixture)
	id := int64(999)
	_, err := service.GetRequired(context.Background(), &id)
	requireError(t, err, "USER_NOT_FOUND", "Benutzer nicht gefunden")
	if querier.queryRowCalls != 1 {
		t.Fatalf("repository calls = %d, want 1", querier.queryRowCalls)
	}
}

func TestUserServiceGetUserOrThrowThrowsWhenUserIDIsNil(t *testing.T) {
	fixture := newPackageBFixture(t)
	service, querier := userServiceWithQueryCount(fixture)
	_, err := service.GetRequired(context.Background(), nil)
	requireError(t, err, "INVALID_ARGUMENT", "User ID must not be null")
	if querier.queryRowCalls != 0 {
		t.Fatalf("repository calls = %d, want 0", querier.queryRowCalls)
	}
}

func TestUserServiceGetUserOrThrowHandlesDifferentRoles(t *testing.T) {
	fixture := newPackageBFixture(t)
	admin := saveTestUser(t, fixture, "admin@example.com", "Admin", "User", domain.Admin)
	admin.State = domain.Brandenburg
	admin = mustValue(fixture.userRepo.Save(context.Background(), admin))
	service, querier := userServiceWithQueryCount(fixture)
	result := mustValue(service.GetRequired(context.Background(), &admin.ID))
	if result.Role != domain.Admin || result.ID != admin.ID {
		t.Fatalf("result = %+v", result)
	}
	if querier.queryRowCalls != 1 {
		t.Fatalf("repository calls = %d, want 1", querier.queryRowCalls)
	}
}

func TestUserServiceGetUserOrThrowHandlesInactiveUsers(t *testing.T) {
	fixture := newPackageBFixture(t)
	user := saveTestUser(t, fixture, "inactive@example.com", "Inactive", "User", domain.User)
	user.Active = false
	user = mustValue(fixture.userRepo.Save(context.Background(), user))
	service, querier := userServiceWithQueryCount(fixture)
	result := mustValue(service.GetRequired(context.Background(), &user.ID))
	if result.Active || result.ID != user.ID {
		t.Fatalf("result = %+v", result)
	}
	if querier.queryRowCalls != 1 {
		t.Fatalf("repository calls = %d, want 1", querier.queryRowCalls)
	}
}

func TestUserServiceGetUserOrThrowHandlesUsersFromDifferentStates(t *testing.T) {
	fixture := newPackageBFixture(t)
	user := saveTestUser(
		t,
		fixture,
		"brandenburg@example.com",
		"Brandenburg",
		"User",
		domain.User,
	)
	user.State = domain.Brandenburg
	user.HalfDayHolidaysEnabled = true
	user = mustValue(fixture.userRepo.Save(context.Background(), user))
	service, querier := userServiceWithQueryCount(fixture)
	result := mustValue(service.GetRequired(context.Background(), &user.ID))
	if result.State != domain.Brandenburg || result.ID != user.ID {
		t.Fatalf("result = %+v", result)
	}
	if querier.queryRowCalls != 1 {
		t.Fatalf("repository calls = %d, want 1", querier.queryRowCalls)
	}
}

func TestUserServiceGetUserOrThrowQueriesRepositoryEachTime(t *testing.T) {
	fixture := newPackageBFixture(t)
	user := saveTestUser(t, fixture, "test@example.com", "Test", "User", domain.User)
	service, querier := userServiceWithQueryCount(fixture)
	mustValue(service.GetRequired(context.Background(), &user.ID))
	mustValue(service.GetRequired(context.Background(), &user.ID))
	if querier.queryRowCalls != 2 {
		t.Fatalf("repository calls = %d, want 2", querier.queryRowCalls)
	}
}
