package integration_test

import (
	"context"
	"strings"
	"testing"

	"github.com/steffbo/timetrack/backend/internal/domain"
)

func TestGenerateInviteTokenCreatesTokenAndURL(t *testing.T) {
	fixture := newPackageBFixture(t)
	user := saveTestUser(t, fixture, "invite@test.local", "Invite", "User", domain.User)
	response := mustValue(fixture.users.Invite(context.Background(), user.ID))
	if !strings.Contains(response.InviteUrl, "/register/") || response.ExpiresAt.IsZero() {
		t.Fatalf("response = %+v", response)
	}
	var token string
	err := harness.Pool.QueryRow(
		context.Background(),
		`SELECT token FROM invite_tokens WHERE user_id=$1`,
		user.ID,
	).Scan(&token)
	if err != nil || !strings.Contains(response.InviteUrl, token) {
		t.Fatalf("token=%q response=%+v err=%v", token, response, err)
	}
}

func TestGenerateInviteTokenInvalidatesOldToken(t *testing.T) {
	fixture := newPackageBFixture(t)
	user := saveTestUser(t, fixture, "invite@test.local", "Invite", "User", domain.User)
	mustValue(fixture.users.Invite(context.Background(), user.ID))
	var firstToken string
	check(t, harness.Pool.QueryRow(
		context.Background(),
		`SELECT token FROM invite_tokens WHERE user_id=$1`,
		user.ID,
	).Scan(&firstToken))
	mustValue(fixture.users.Invite(context.Background(), user.ID))
	var secondToken string
	check(t, harness.Pool.QueryRow(
		context.Background(),
		`SELECT token FROM invite_tokens WHERE user_id=$1`,
		user.ID,
	).Scan(&secondToken))
	var oldCount int
	check(t, harness.Pool.QueryRow(
		context.Background(),
		`SELECT count(*) FROM invite_tokens WHERE token=$1`,
		firstToken,
	).Scan(&oldCount))
	if firstToken == secondToken || oldCount != 0 {
		t.Fatalf("first=%q second=%q oldCount=%d", firstToken, secondToken, oldCount)
	}
}

func TestGenerateInviteTokenUserNotFound(t *testing.T) {
	fixture := newPackageBFixture(t)
	_, err := fixture.users.Invite(context.Background(), 999)
	requireError(t, err, "USER_NOT_FOUND", "Benutzer nicht gefunden")
}
