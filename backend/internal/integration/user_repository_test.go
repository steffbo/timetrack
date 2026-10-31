package integration_test

import (
	"context"
	"testing"

	"github.com/steffbo/timetrack/backend/internal/domain"
	"github.com/steffbo/timetrack/backend/internal/users"
)

func repositoryTestUser(t *testing.T) (packageBFixture, users.User) {
	t.Helper()
	fixture := newPackageBFixture(t)
	return fixture, users.User{
		Email:        "test@example.com",
		PasswordHash: stringPointer("$2a$10$hashedPassword"),
		FirstName:    "Test",
		LastName:     "User",
		Role:         domain.User,
		Active:       true,
		State:        domain.Berlin,
	}
}

func stringPointer(value string) *string { return &value }

func TestUserRepositoryShouldSaveAndFindUserByID(t *testing.T) {
	fixture, user := repositoryTestUser(t)
	saved := mustValue(fixture.userRepo.Save(context.Background(), user))
	if saved.ID == 0 || saved.CreatedAt.IsZero() || saved.UpdatedAt.IsZero() {
		t.Fatalf("missing generated fields: %+v", saved)
	}
	found, present, err := fixture.userRepo.FindByID(context.Background(), saved.ID)
	if err != nil || !present || found.Email != "test@example.com" {
		t.Fatalf("found=%+v present=%v err=%v", found, present, err)
	}
}

func TestUserRepositoryShouldFindUserByEmail(t *testing.T) {
	fixture, user := repositoryTestUser(t)
	mustValue(fixture.userRepo.Save(context.Background(), user))
	found, present, err := fixture.userRepo.FindByEmail(context.Background(), "test@example.com")
	if err != nil || !present || found.FirstName != "Test" || found.LastName != "User" {
		t.Fatalf("found=%+v present=%v err=%v", found, present, err)
	}
}

func TestUserRepositoryShouldReturnEmptyWhenEmailNotFound(t *testing.T) {
	fixture, _ := repositoryTestUser(t)
	_, present, err := fixture.userRepo.FindByEmail(
		context.Background(),
		"nonexistent@example.com",
	)
	if err != nil || present {
		t.Fatalf("present=%v err=%v", present, err)
	}
}

func TestUserRepositoryShouldCheckIfUserExistsByEmail(t *testing.T) {
	fixture, user := repositoryTestUser(t)
	mustValue(fixture.userRepo.Save(context.Background(), user))
	exists := mustValue(fixture.userRepo.ExistsByEmail(context.Background(), "test@example.com"))
	notExists := mustValue(fixture.userRepo.ExistsByEmail(context.Background(), "nonexistent@example.com"))
	if !exists || notExists {
		t.Fatalf("exists=%v notExists=%v", exists, notExists)
	}
}

func TestUserRepositoryShouldDeleteUserByID(t *testing.T) {
	fixture, user := repositoryTestUser(t)
	saved := mustValue(fixture.userRepo.Save(context.Background(), user))
	if err := fixture.userRepo.DeleteByID(context.Background(), saved.ID); err != nil {
		t.Fatal(err)
	}
	_, present, err := fixture.userRepo.FindByID(context.Background(), saved.ID)
	if err != nil || present {
		t.Fatalf("present=%v err=%v", present, err)
	}
}

func TestUserRepositoryShouldGetFullNameCorrectly(t *testing.T) {
	fixture, user := repositoryTestUser(t)
	saved := mustValue(fixture.userRepo.Save(context.Background(), user))
	if saved.FullName() != "Test User" {
		t.Fatalf("full name = %q", saved.FullName())
	}
}

func TestUserRepositoryShouldCheckIfUserIsAdmin(t *testing.T) {
	fixture, regular := repositoryTestUser(t)
	regular = mustValue(fixture.userRepo.Save(context.Background(), regular))
	admin := mustValue(fixture.userRepo.Save(context.Background(), users.User{
		Email:        "admin@example.com",
		PasswordHash: stringPointer("$2a$10$hashedPassword"),
		FirstName:    "Admin",
		LastName:     "User",
		Role:         domain.Admin,
		Active:       true,
		State:        domain.Berlin,
	}))
	if regular.IsAdmin() || !admin.IsAdmin() {
		t.Fatalf("regular admin=%v, admin admin=%v", regular.IsAdmin(), admin.IsAdmin())
	}
}
