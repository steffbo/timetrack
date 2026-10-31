// Package testutil supplies one PostgreSQL 17 container per package TestMain.
package testutil

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/steffbo/timetrack/backend/internal/api"
	"github.com/steffbo/timetrack/backend/internal/clock"
	"github.com/steffbo/timetrack/backend/internal/config"
	"github.com/steffbo/timetrack/backend/internal/db"
	"github.com/steffbo/timetrack/backend/internal/domain"
	"github.com/steffbo/timetrack/backend/internal/migrate"
	"github.com/steffbo/timetrack/backend/internal/server"
	"github.com/testcontainers/testcontainers-go"
	"github.com/testcontainers/testcontainers-go/modules/postgres"
	"github.com/testcontainers/testcontainers-go/wait"
	"golang.org/x/crypto/bcrypt"
)

type Harness struct {
	Pool      *pgxpool.Pool
	Container *postgres.PostgresContainer
}

func Start(ctx context.Context) (*Harness, error) {
	container, err := postgres.Run(ctx, "postgres:17-alpine", postgres.WithDatabase("timetrack"),
		postgres.WithUsername("timetrack"), postgres.WithPassword("timetrack"),
		testcontainers.WithWaitStrategy(wait.ForLog("database system is ready to accept connections").
			WithOccurrence(2).WithStartupTimeout(time.Minute)))
	if err != nil {
		return nil, err
	}
	dsn, err := container.ConnectionString(ctx, "sslmode=disable")
	if err != nil {
		_ = container.Terminate(ctx)
		return nil, err
	}
	pool, err := db.Open(ctx, dsn)
	if err != nil {
		_ = container.Terminate(ctx)
		return nil, err
	}
	h := &Harness{pool, container}
	if err = migrate.Run(ctx, pool); err != nil {
		h.Close(ctx)
		return nil, err
	}
	return h, nil
}
func (h *Harness) Close(ctx context.Context) { h.Pool.Close(); _ = h.Container.Terminate(ctx) }
func (h *Harness) Reset(t testing.TB) {
	t.Helper()
	_, err := h.Pool.Exec(context.Background(), `TRUNCATE users RESTART IDENTITY CASCADE`)
	if err != nil {
		t.Fatal(err)
	}
}
func (h *Harness) User(t testing.TB, email string, role domain.Role, password *string) int64 {
	t.Helper()
	var hash *string
	if password != nil {
		b, err := bcrypt.GenerateFromPassword([]byte(*password), 10)
		if err != nil {
			t.Fatal(err)
		}
		v := string(b)
		hash = &v
	}
	var id int64
	err := h.Pool.QueryRow(context.Background(), `INSERT INTO users
 (email,password_hash,first_name,last_name,role,active,state) VALUES($1,$2,'Test','User',$3,true,'BERLIN')
 RETURNING id`, email, hash, role).Scan(&id)
	if err != nil {
		t.Fatal(err)
	}
	return id
}
func (h *Harness) Server(t testing.TB, k clock.Clock) (*server.Server, *httptest.Server) {
	t.Helper()
	c := config.Config{JWTSecret: "your-secret-key-change-this-in-production-min-256-bits",
		AccessExpiration: 24 * time.Hour, RefreshExpiration: 180 * 24 * time.Hour, FrontendBaseURL: "http://localhost:5173"}
	app := server.New(h.Pool, c, k)
	httpServer := httptest.NewServer(app.Router())
	t.Cleanup(httpServer.Close)
	return app, httpServer
}
func Request(t testing.TB, baseURL, method, path, token string, body any) (int, []byte) {
	t.Helper()
	var reader io.Reader
	if body != nil {
		b, err := json.Marshal(body)
		if err != nil {
			t.Fatal(err)
		}
		reader = bytes.NewReader(b)
	}
	req, err := http.NewRequest(method, baseURL+path, reader)
	if err != nil {
		t.Fatal(err)
	}
	if body != nil {
		req.Header.Set("Content-Type", "application/json")
	}
	if token != "" {
		req.Header.Set("Authorization", "Bearer "+token)
	}
	res, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatal(err)
	}
	defer res.Body.Close()
	b, err := io.ReadAll(res.Body)
	if err != nil {
		t.Fatal(err)
	}
	return res.StatusCode, b
}
func Login(t testing.TB, baseURL, email, password string) api.AuthResponse {
	t.Helper()
	status, b := Request(t, baseURL, "POST", "/api/auth/login", "", map[string]string{"email": email, "password": password})
	if status != 200 {
		t.Fatal(fmt.Sprintf("login status %d: %s", status, b))
	}
	var out api.AuthResponse
	if err := json.Unmarshal(b, &out); err != nil {
		t.Fatal(err)
	}
	return out
}
