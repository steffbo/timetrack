package integration_test

import (
	"context"
	"testing"

	"github.com/steffbo/timetrack/backend/internal/api"
	"github.com/steffbo/timetrack/backend/internal/auth"
	"github.com/steffbo/timetrack/backend/internal/domain"
)

func TestImpersonateUserAdminImpersonatesRegularUser(t *testing.T) {
	fixture := newPackageBFixture(t)
	admin := saveTestUser(t, fixture, "admin@test.local", "Admin", "User", domain.Admin)
	user := saveTestUser(t, fixture, "user@test.local", "Regular", "User", domain.User)
	ctx := auth.WithPrincipal(context.Background(), principalFor(admin))
	response := mustValue(fixture.app.Auth.Impersonate(ctx, user.ID))
	if response.AccessToken == "" || response.RefreshToken == "" || response.TokenType != "Bearer" {
		t.Fatalf("response = %+v", response)
	}
	if response.User == nil || response.User.Id != user.ID ||
		string(response.User.Email) != user.Email || response.User.Role != api.USER {
		t.Fatalf("response user = %+v", response.User)
	}
	var userTokens, adminTokens int
	var storedToken string
	check(t, harness.Pool.QueryRow(
		context.Background(),
		`SELECT count(*),min(token) FROM refresh_tokens WHERE user_id=$1`,
		user.ID,
	).Scan(&userTokens, &storedToken))
	check(t, harness.Pool.QueryRow(
		context.Background(),
		`SELECT count(*) FROM refresh_tokens WHERE user_id=$1`,
		admin.ID,
	).Scan(&adminTokens))
	if userTokens != 1 || storedToken != response.RefreshToken || adminTokens != 0 {
		t.Fatalf("userTokens=%d stored=%q adminTokens=%d", userTokens, storedToken, adminTokens)
	}
}

func TestImpersonateUserNonAdminCannotImpersonate(t *testing.T) {
	fixture := newPackageBFixture(t)
	user := saveTestUser(t, fixture, "user@test.local", "Regular", "User", domain.User)
	other := saveTestUser(t, fixture, "other@test.local", "Other", "User", domain.User)
	ctx := auth.WithPrincipal(context.Background(), principalFor(user))
	_, err := fixture.app.Auth.Impersonate(ctx, other.ID)
	requireError(t, err, "FORBIDDEN", "Nur Administratoren können andere Benutzer verkörpern")
}

func TestImpersonateUserAdminCannotImpersonateAdmin(t *testing.T) {
	fixture := newPackageBFixture(t)
	admin := saveTestUser(t, fixture, "admin@test.local", "Admin", "User", domain.Admin)
	other := saveTestUser(t, fixture, "other-admin@test.local", "Other", "Admin", domain.Admin)
	ctx := auth.WithPrincipal(context.Background(), principalFor(admin))
	_, err := fixture.app.Auth.Impersonate(ctx, other.ID)
	requireError(
		t,
		err,
		"FORBIDDEN",
		"Administratoren können nicht andere Administratoren verkörpern",
	)
}
