package server

import (
	"net/http"

	"github.com/steffbo/timetrack/backend/internal/httpx"
)

func (s *Server) ManagementRouter() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /actuator/health", func(w http.ResponseWriter, r *http.Request) {
		status := 200
		health := "UP"
		if s.Pool.Ping(r.Context()) != nil {
			status = 503
			health = "DOWN"
		}
		httpx.JSON(w, status, map[string]any{"status": health,
			"components": map[string]any{"db": map[string]string{"status": health}}})
	})
	mux.HandleFunc("GET /actuator/info", func(w http.ResponseWriter, r *http.Request) { httpx.JSON(w, 200, map[string]any{}) })
	return mux
}
