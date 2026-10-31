package auth

import (
	"context"
	"fmt"
	"strconv"
	"time"

	"github.com/golang-jwt/jwt/v5"
	"github.com/steffbo/timetrack/backend-go/internal/clock"
	"github.com/steffbo/timetrack/backend-go/internal/domain"
	"github.com/steffbo/timetrack/backend-go/internal/httpx"
)

type Principal struct {
	Authorities    string
	ID             int64
	Email          string
	Role           domain.Role
	Active         bool
	ImpersonatedBy *int64
}
type contextKey struct{}

func WithPrincipal(ctx context.Context, p Principal) context.Context {
	return context.WithValue(ctx, contextKey{}, p)
}
func Current(ctx context.Context) (Principal, bool) {
	p, ok := ctx.Value(contextKey{}).(Principal)
	return p, ok
}
func RequireRole(ctx context.Context, role domain.Role) error {
	p, ok := Current(ctx)
	if !ok || p.Role != role {
		return httpx.Forbidden("Access Denied")
	}
	return nil
}
func ValidateOwnership(ownerID, userID *int64, resourceType string) error {
	if ownerID == nil || userID == nil {
		return httpx.InvalidArgument("Owner ID and authenticated user ID must not be null")
	}
	if *ownerID != *userID {
		return httpx.Forbidden("Sie haben keine Berechtigung, " + resourceType + " zu ändern")
	}
	return nil
}

type Claims struct {
	Email          string `json:"email"`
	Authorities    string `json:"authorities"`
	ImpersonatedBy *int64 `json:"impersonatedBy,omitempty"`
	jwt.RegisteredClaims
}
type Tokens struct {
	Secret                              []byte
	Clock                               clock.Clock
	AccessExpiration, RefreshExpiration time.Duration
}

func (t Tokens) Algorithm() *jwt.SigningMethodHMAC {
	switch {
	case len(t.Secret) >= 64:
		return jwt.SigningMethodHS512
	case len(t.Secret) >= 48:
		return jwt.SigningMethodHS384
	default:
		return jwt.SigningMethodHS256
	}
}
func (t Tokens) Generate(p Principal, expiration time.Duration) (string, error) {
	if len(t.Secret) < 32 {
		return "", fmt.Errorf("JWT key shorter than 256 bits")
	}
	now := t.Clock.Now()
	authorities := p.Authorities
	if authorities == "" {
		authorities = "ROLE_" + string(p.Role)
	}
	c := Claims{Email: p.Email, Authorities: authorities, ImpersonatedBy: p.ImpersonatedBy,
		RegisteredClaims: jwt.RegisteredClaims{Subject: strconv.FormatInt(p.ID, 10),
			IssuedAt: jwt.NewNumericDate(now), ExpiresAt: jwt.NewNumericDate(now.Add(expiration))}}
	token := jwt.NewWithClaims(t.Algorithm(), c)
	delete(token.Header, "typ") // JJWT does not add a typ header.
	return token.SignedString(t.Secret)
}
func (t Tokens) Parse(raw string) (*Claims, error) {
	c := new(Claims)
	_, err := jwt.ParseWithClaims(raw, c, func(token *jwt.Token) (any, error) {
		m, ok := token.Method.(*jwt.SigningMethodHMAC)
		if !ok || len(t.Secret)*8 < m.Hash.Size()*8 {
			return nil, fmt.Errorf("unsupported JWT algorithm or weak key")
		}
		return t.Secret, nil
	}, jwt.WithValidMethods([]string{"HS256", "HS384", "HS512"}), jwt.WithTimeFunc(func() time.Time { return t.Clock.Now().Truncate(time.Millisecond) }),
		// JJWT rejects only when Date.now() is strictly after expiration, at millisecond precision.
		jwt.WithLeeway(time.Nanosecond))
	if err != nil {
		return nil, err
	}
	if _, err = strconv.ParseInt(c.Subject, 10, 64); err != nil {
		return nil, err
	}
	return c, nil
}
