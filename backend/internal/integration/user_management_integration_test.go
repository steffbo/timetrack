package integration_test

import (
	"context"
	"strings"
	"testing"

	"github.com/oapi-codegen/runtime/types"
	"github.com/steffbo/timetrack/backend/internal/api"
	"github.com/steffbo/timetrack/backend/internal/domain"
	"github.com/steffbo/timetrack/backend/internal/users"
	"golang.org/x/crypto/bcrypt"
)

type userManagementFixture struct {
	packageBFixture
	admin users.User
	user  users.User
}

func newUserManagementFixture(t *testing.T) userManagementFixture {
	t.Helper()
	fixture := newPackageBFixture(t)
	return userManagementFixture{
		packageBFixture: fixture,
		admin: saveTestUser(
			t,
			fixture,
			"admin@test.local",
			"Admin",
			"User",
			domain.Admin,
		),
		user: saveTestUser(
			t,
			fixture,
			"user@test.local",
			"Regular",
			"User",
			domain.User,
		),
	}
}

func createUserRequest(email, first, last string) api.CreateUserRequest {
	password := "password123"
	active := true
	return api.CreateUserRequest{
		Email:     types.Email(email),
		Password:  &password,
		FirstName: first,
		LastName:  last,
		Role:      api.CreateUserRequestRoleUSER,
		Active:    &active,
	}
}

func TestUserManagementCreateUserSuccess(t *testing.T) {
	fixture := newUserManagementFixture(t)
	response := mustValue(fixture.users.Create(
		context.Background(),
		createUserRequest("newuser@test.local", "New", "User"),
	),
	)
	if response.Id == 0 || string(response.Email) != "newuser@test.local" ||
		response.FirstName == nil || *response.FirstName != "New" ||
		response.LastName == nil || *response.LastName != "User" ||
		response.Role != api.USER || !response.Active {
		t.Fatalf("response = %+v", response)
	}
	var count int
	check(t, harness.Pool.QueryRow(
		context.Background(),
		`SELECT count(*) FROM working_hours`,
	).Scan(&count))
	if count != 7 {
		t.Fatalf("working hours count = %d", count)
	}
}

func TestUserManagementCreateUserDuplicateEmail(t *testing.T) {
	fixture := newUserManagementFixture(t)
	_, err := fixture.users.Create(
		context.Background(),
		createUserRequest(fixture.user.Email, "Duplicate", "User"),
	)
	requireError(t, err, "DUPLICATE_EMAIL", "Email bereits vergeben")
}

func TestUserManagementGetUserAdminGetsAnyUser(t *testing.T) {
	fixture := newUserManagementFixture(t)
	response := mustValue(fixture.users.GetFor(
		context.Background(),
		fixture.user.ID,
		principalFor(fixture.admin),
	))
	if response.Id != fixture.user.ID || string(response.Email) != fixture.user.Email {
		t.Fatalf("response = %+v", response)
	}
}

func TestUserManagementGetUserUserGetsOwnProfile(t *testing.T) {
	fixture := newUserManagementFixture(t)
	response := mustValue(fixture.users.GetFor(
		context.Background(),
		fixture.user.ID,
		principalFor(fixture.user),
	))
	if response.Id != fixture.user.ID {
		t.Fatalf("response = %+v", response)
	}
}

func TestUserManagementGetUserUserCannotGetOtherUser(t *testing.T) {
	fixture := newUserManagementFixture(t)
	_, err := fixture.users.GetFor(
		context.Background(),
		fixture.admin.ID,
		principalFor(fixture.user),
	)
	requireError(t, err, "FORBIDDEN", "Keine Berechtigung")
}

func TestUserManagementGetCurrentUserSuccess(t *testing.T) {
	fixture := newUserManagementFixture(t)
	response := mustValue(fixture.users.Get(context.Background(), fixture.user.ID))
	if response.Id != fixture.user.ID || string(response.Email) != fixture.user.Email {
		t.Fatalf("response = %+v", response)
	}
}

func TestUserManagementGetUserNotFound(t *testing.T) {
	fixture := newUserManagementFixture(t)
	_, err := fixture.users.GetFor(
		context.Background(),
		999,
		principalFor(fixture.admin),
	)
	requireError(t, err, "USER_NOT_FOUND", "Benutzer nicht gefunden")
}

func TestUserManagementUpdateUserUpdatesOwnProfile(t *testing.T) {
	fixture := newUserManagementFixture(t)
	first, last := "Updated", "Name"
	response := mustValue(fixture.users.Update(
		context.Background(),
		fixture.user.ID,
		api.UpdateUserRequest{FirstName: &first, LastName: &last},
		principalFor(fixture.user),
	))
	if response.FirstName == nil || *response.FirstName != first ||
		response.LastName == nil || *response.LastName != last {
		t.Fatalf("response = %+v", response)
	}
}

func TestUserManagementUpdateUserCannotUpdateOtherUser(t *testing.T) {
	fixture := newUserManagementFixture(t)
	first := "Hacker"
	_, err := fixture.users.Update(
		context.Background(),
		fixture.admin.ID,
		api.UpdateUserRequest{FirstName: &first},
		principalFor(fixture.user),
	)
	requireError(t, err, "FORBIDDEN", "Keine Berechtigung")
}

func TestUserManagementUpdateUserCannotChangeOwnRole(t *testing.T) {
	fixture := newUserManagementFixture(t)
	role := api.UpdateUserRequestRoleADMIN
	_, err := fixture.users.Update(
		context.Background(),
		fixture.user.ID,
		api.UpdateUserRequest{Role: &role},
		principalFor(fixture.user),
	)
	requireError(t, err, "FORBIDDEN", "Keine Berechtigung")
}

func TestUserManagementUpdateAdminCanChangeRole(t *testing.T) {
	fixture := newUserManagementFixture(t)
	role := api.UpdateUserRequestRoleADMIN
	response := mustValue(fixture.users.Update(
		context.Background(),
		fixture.user.ID,
		api.UpdateUserRequest{Role: &role},
		principalFor(fixture.admin),
	))
	if response.Role != api.ADMIN {
		t.Fatalf("response = %+v", response)
	}
}

func TestUserManagementUpdateAdminCanChangeActiveStatus(t *testing.T) {
	fixture := newUserManagementFixture(t)
	active := false
	response := mustValue(fixture.users.Update(
		context.Background(),
		fixture.user.ID,
		api.UpdateUserRequest{Active: &active},
		principalFor(fixture.admin),
	))
	if response.Active {
		t.Fatalf("response = %+v", response)
	}
}

func TestUserManagementUpdateDuplicateEmail(t *testing.T) {
	fixture := newUserManagementFixture(t)
	email := types.Email(fixture.admin.Email)
	_, err := fixture.users.Update(
		context.Background(),
		fixture.user.ID,
		api.UpdateUserRequest{Email: &email},
		principalFor(fixture.user),
	)
	requireError(t, err, "DUPLICATE_EMAIL", "Email bereits vergeben")
}

func TestUserManagementUpdatePasswordChange(t *testing.T) {
	fixture := newUserManagementFixture(t)
	password := "newpassword123"
	mustValue(fixture.users.Update(
		context.Background(),
		fixture.user.ID,
		api.UpdateUserRequest{Password: &password},
		principalFor(fixture.user),
	))
	updated, present, err := fixture.userRepo.FindByID(context.Background(), fixture.user.ID)
	if err != nil || !present || updated.PasswordHash == nil ||
		bcrypt.CompareHashAndPassword([]byte(*updated.PasswordHash), []byte(password)) != nil {
		t.Fatalf("updated=%+v present=%v err=%v", updated, present, err)
	}
}

func TestUserManagementDeleteUserSuccess(t *testing.T) {
	fixture := newUserManagementFixture(t)
	if err := fixture.users.Delete(context.Background(), fixture.user.ID); err != nil {
		t.Fatal(err)
	}
	_, present, err := fixture.userRepo.FindByID(context.Background(), fixture.user.ID)
	if err != nil || present {
		t.Fatalf("present=%v err=%v", present, err)
	}
}

func TestUserManagementDeleteUserNotFound(t *testing.T) {
	fixture := newUserManagementFixture(t)
	err := fixture.users.Delete(context.Background(), 999)
	requireError(t, err, "USER_NOT_FOUND", "Benutzer nicht gefunden")
}

func TestUserManagementCreatePendingWithInviteURL(t *testing.T) {
	fixture := newUserManagementFixture(t)
	request := createUserRequest("pending@test.local", "Pending", "User")
	request.Password = nil
	response := mustValue(fixture.users.Create(context.Background(), request))
	if response.Pending == nil || !*response.Pending || response.InviteUrl == nil ||
		*response.InviteUrl == "" || !strings.Contains(*response.InviteUrl, "/register/") {
		t.Fatalf("response = %+v", response)
	}
	saved, present, err := fixture.userRepo.FindByID(context.Background(), response.Id)
	if err != nil || !present || saved.PasswordHash != nil {
		t.Fatalf("saved=%+v present=%v err=%v", saved, present, err)
	}
}

func TestUserManagementCreateWithPasswordNotPending(t *testing.T) {
	fixture := newUserManagementFixture(t)
	response := mustValue(fixture.users.Create(
		context.Background(),
		createUserRequest("withpw@test.local", "Has", "Password"),
	))
	if response.Pending == nil || *response.Pending || response.InviteUrl != nil {
		t.Fatalf("response = %+v", response)
	}
}
