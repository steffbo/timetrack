package auth

import (
	"os"
	"strings"
	"testing"
	"time"

	"github.com/steffbo/timetrack/backend-go/internal/clock"
	"github.com/steffbo/timetrack/backend-go/internal/domain"
	"golang.org/x/crypto/bcrypt"
)

// Fixed fixtures generated using JJWT 0.12.6, Java 25, Keys.hmacShaKeyFor and JwtTokenProvider claims.
func TestJavaJWTFixtures(t *testing.T) {
	now := time.Unix(1894708800, 0)
	for _, tc := range []struct {
		size  int
		token string
	}{
		{32, "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI0MiIsImVtYWlsIjoiamF2YUB0ZXN0LmxvY2FsIiwiYXV0aG9yaXRp" +
			"ZXMiOiJST0xFX0FETUlOIiwiaWF0IjoxODk0NzA4ODAwLCJleHAiOjE4OTQ3OTUyMDAsImltcGVyc29uYXRlZ" +
			"EJ5Ijo3fQ.GojSNf0aVanmQwgJ05R-zYT5irnbyY-gyPbV34kNNLk"},
		{48, "eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiI0MiIsImVtYWlsIjoiamF2YUB0ZXN0LmxvY2FsIiwiYXV0aG9yaXRp" +
			"ZXMiOiJST0xFX0FETUlOIiwiaWF0IjoxODk0NzA4ODAwLCJleHAiOjE4OTQ3OTUyMDAsImltcGVyc29uYXRlZ" +
			"EJ5Ijo3fQ.738f2PmqeZBrF4o79sqZYpUI1Lkw7yFuJB-Vj7AFiLLKcdMzGWhKXfR-PvrBbdps"},
		{64, "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiI0MiIsImVtYWlsIjoiamF2YUB0ZXN0LmxvY2FsIiwiYXV0aG9yaXRp" +
			"ZXMiOiJST0xFX0FETUlOIiwiaWF0IjoxODk0NzA4ODAwLCJleHAiOjE4OTQ3OTUyMDAsImltcGVyc29uYXRlZ" +
			"EJ5Ijo3fQ.1ns-0fNm_WsA2jT6PZXJxslZt4v3HFmZjdvxyWKT1XLu9I1pvJuR_7PAplSoIJG10Vf1K4RTmVx" +
			"1PWnyztj2cw"},
	} {
		provider := Tokens{Secret: []byte(strings.Repeat("x", tc.size)), Clock: clock.Fixed{Time: now}}
		claims, err := provider.Parse(tc.token)
		if err != nil {
			t.Fatal(err)
		}
		if claims.Subject != "42" || claims.Email != "java@test.local" || claims.Authorities != "ROLE_ADMIN" ||
			claims.ImpersonatedBy == nil || *claims.ImpersonatedBy != 7 {
			t.Fatal(claims)
		}
		// Generate, then verify independently with JJWT via scripts/interop/Reference.java.
		p := Principal{ID: 42, Email: "java@test.local", Role: domain.Admin, ImpersonatedBy: claims.ImpersonatedBy}
		generated, err := provider.Generate(p, 24*time.Hour)
		if err != nil {
			t.Fatal(err)
		}
		if _, err = provider.Parse(generated); err != nil {
			t.Fatal(err)
		}
		provider.Clock = clock.Fixed{Time: now.Add(25 * time.Hour)}
		if _, err = provider.Parse(tc.token); err == nil {
			t.Fatal("expired token accepted")
		}
		if _, err = provider.Parse(tc.token + "x"); err == nil {
			t.Fatal("tampered signature accepted")
		}
	}
}
func TestBcryptJavaMigrationHash(t *testing.T) {
	hash := "$2a$10$j6u7LLRjQfVQIlu4FJbQtulaQTGxqoSP.dEVQmEIr2rjocFyM3zOe"
	if err := bcrypt.CompareHashAndPassword([]byte(hash), []byte("admin1")); err != nil {
		t.Fatal(err)
	}
	if err := bcrypt.CompareHashAndPassword([]byte(hash), []byte("admin")); err == nil {
		t.Fatal("wrong password accepted")
	}
}
func TestExportGoJWT(t *testing.T) {
	path := os.Getenv("TIMETRACK_INTEROP_TOKEN_FILE")
	if path == "" {
		return
	}
	provider := Tokens{Secret: []byte("your-secret-key-change-this-in-production-min-256-bits"),
		Clock: clock.Fixed{Time: time.Unix(1894708800, 0)}}
	token, err := provider.Generate(Principal{ID: 42, Email: "go@test.local", Role: domain.User}, 24*time.Hour)
	if err != nil {
		t.Fatal(err)
	}
	if err = os.WriteFile(path, []byte(token), 0600); err != nil {
		t.Fatal(err)
	}
}

func TestJjwtExpirationBoundary(t *testing.T) {
	now := time.Unix(1894708800, 0)
	provider := Tokens{Secret: []byte(strings.Repeat("x", 32)), Clock: clock.Fixed{Time: now}}
	token, err := provider.Generate(Principal{ID: 1, Email: "test@test.local", Role: domain.User}, time.Second)
	if err != nil {
		t.Fatal(err)
	}
	provider.Clock = clock.Fixed{Time: now.Add(time.Second + 999*time.Microsecond)}
	if _, err = provider.Parse(token); err != nil {
		t.Fatal("JJWT still accepts this millisecond", err)
	}
	provider.Clock = clock.Fixed{Time: now.Add(time.Second + time.Millisecond)}
	if _, err = provider.Parse(token); err == nil {
		t.Fatal("expired JWT accepted")
	}
}
