package integration_test

import (
	"strings"
	"testing"

	"github.com/steffbo/timetrack/backend/internal/domain"
	"github.com/steffbo/timetrack/backend/internal/testutil"
)

func TestUnknownAPIRouteReturnsEstablishedErrorBody(t *testing.T) {
	harness.Reset(t)
	pw := "password"
	harness.User(t, "user@test.local", domain.User, &pw)
	_, srv := harness.Server(t, fixed)
	session := testutil.Login(t, srv.URL, "user@test.local", pw)
	status, b := testutil.Request(t, srv.URL, "GET", "/api/nonexistent", session.AccessToken, nil)
	errorCode(t, status, b, 500, "INTERNAL_ERROR", "Ein interner Fehler ist aufgetreten")
	if !strings.Contains(string(b), `No static resource api/nonexistent for request '/api/nonexistent'.`) {
		t.Fatalf("details: %s", b)
	}
}
