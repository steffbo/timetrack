package server

import (
	"context"
	"net/http"
	"strings"

	"github.com/steffbo/timetrack/backend/internal/api"
	"github.com/steffbo/timetrack/backend/internal/auth"
	"github.com/steffbo/timetrack/backend/internal/domain"
	"github.com/steffbo/timetrack/backend/internal/httpx"
)

func (s *Server) security(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		r = r.WithContext(httpx.WithLocale(r.Context(), r.Header.Get("Accept-Language")))
		if raw, ok := strings.CutPrefix(r.Header.Get("Authorization"), "Bearer "); ok {
			if claims, err := s.Auth.Tokens.Parse(raw); err == nil {
				// Reload the principal by email and use current database authorities.
				if p, err := s.Auth.ByEmail(r.Context(), claims.Email); err == nil {
					p.ImpersonatedBy = claims.ImpersonatedBy
					r = r.WithContext(auth.WithPrincipal(r.Context(), p))
				}
			}
		}
		_, authenticated := auth.Current(r.Context())
		if strings.HasPrefix(r.URL.Path, "/api/") && !strings.HasPrefix(r.URL.Path, "/api/auth/") && !authenticated {
			httpx.EntryPoint(w, r, s.Clock)
			return
		}
		next.ServeHTTP(w, r)
	})
}
func (s *Server) roles(next api.StrictHandlerFunc, operation string) api.StrictHandlerFunc {
	return func(ctx context.Context, w http.ResponseWriter, r *http.Request, request any) (any, error) {
		switch operation {
		case "GetAllUsers", "CreateUser", "DeleteUser", "ImpersonateUser", "GenerateInviteToken", "GetWorkingHoursByUserId":
			if err := auth.RequireRole(ctx, domain.Admin); err != nil {
				// Authorization failures use the central unexpected-error mapping.
				return nil, fmtAccessDenied{}
			}
		}
		return next(ctx, w, r, request)
	}
}

type fmtAccessDenied struct{}

func (fmtAccessDenied) Error() string { return "Access Denied" }
func (s *Server) cors(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		origin := r.Header.Get("Origin")
		if origin != "" {
			// SecurityConfig currently allows all origin patterns, irrespective of ALLOWED_ORIGINS.
			w.Header().Add("Vary", "Origin")
			w.Header().Add("Vary", "Access-Control-Request-Method")
			w.Header().Add("Vary", "Access-Control-Request-Headers")
			method := r.Method
			if method == "OPTIONS" {
				method = r.Header.Get("Access-Control-Request-Method")
			}
			if !strings.Contains(",GET,POST,PUT,DELETE,PATCH,OPTIONS,", ","+method+",") {
				w.WriteHeader(403)
				_, _ = w.Write([]byte("Invalid CORS request"))
				return
			}
			w.Header().Set("Access-Control-Allow-Origin", origin)
			w.Header().Set("Access-Control-Allow-Credentials", "true")
			if r.Method == "OPTIONS" && r.Header.Get("Access-Control-Request-Method") != "" {
				w.Header().Set("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,PATCH,OPTIONS")
				w.Header().Set("Access-Control-Allow-Headers", r.Header.Get("Access-Control-Request-Headers"))
				w.Header().Set("Access-Control-Max-Age", "3600")
				w.WriteHeader(200)
				return
			}
		}
		// Default security response headers.
		w.Header().Set("X-Content-Type-Options", "nosniff")
		w.Header().Set("X-XSS-Protection", "0")
		w.Header().Set("X-Frame-Options", "DENY")
		w.Header().Set("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate")
		w.Header().Set("Pragma", "no-cache")
		w.Header().Set("Expires", "0")
		next.ServeHTTP(w, r)
	})
}
