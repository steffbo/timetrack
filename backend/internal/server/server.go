// Package server composes domain handlers without per-domain router registration.
package server

import (
	"fmt"
	"log/slog"
	"net/http"

	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/steffbo/timetrack/backend/internal/api"
	"github.com/steffbo/timetrack/backend/internal/auth"
	"github.com/steffbo/timetrack/backend/internal/clock"
	"github.com/steffbo/timetrack/backend/internal/config"
	"github.com/steffbo/timetrack/backend/internal/httpx"
	"github.com/steffbo/timetrack/backend/web"
)

type Server struct {
	Pool   *pgxpool.Pool
	Clock  clock.Clock
	Config config.Config
	Auth   *auth.Service
	Logger *slog.Logger
}

var _ api.StrictServerInterface = (*Server)(nil)

func New(p *pgxpool.Pool, c config.Config, k clock.Clock) *Server {
	tokens := auth.Tokens{Secret: []byte(c.JWTSecret), Clock: k,
		AccessExpiration: c.AccessExpiration, RefreshExpiration: c.RefreshExpiration}
	return &Server{Pool: p, Clock: k, Config: c, Logger: slog.Default(),
		Auth: &auth.Service{Pool: p, Tokens: tokens, Clock: k, FrontendBaseURL: c.FrontendBaseURL}}
}
func (s *Server) Router() http.Handler {
	mux := http.NewServeMux()
	fail := func(w http.ResponseWriter, r *http.Request, err error) { httpx.WriteError(w, s.Clock, err) }
	badRequest := func(w http.ResponseWriter, r *http.Request, err error) {
		httpx.WriteError(w, s.Clock, httpx.Validation(err.Error()))
	}
	strict := api.NewStrictHandlerWithOptions(s, []api.StrictMiddlewareFunc{s.roles}, api.StrictHTTPServerOptions{
		RequestErrorHandlerFunc: fail, ResponseErrorHandlerFunc: fail})
	api.HandlerWithOptions(strict, api.StdHTTPServerOptions{BaseRouter: mux, ErrorHandlerFunc: badRequest})
	mux.Handle("/", web.Handler())
	// Unknown paths fall through to static resource lookup and return the established 500 response.
	mux.HandleFunc("/api/", func(w http.ResponseWriter, r *http.Request) {
		fail(w, r, fmt.Errorf("No static resource %s for request '%s'.", r.URL.Path[1:], r.URL.Path))
	})
	return s.cors(s.security(s.validate(compress(mux))))
}
