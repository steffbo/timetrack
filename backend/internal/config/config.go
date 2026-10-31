package config

import (
	"fmt"
	"log/slog"
	"net/url"
	"os"
	"strconv"
	"strings"
	"time"
)

type Config struct {
	DatabaseURL, JWTSecret, ServerPort, ManagementPort, FrontendBaseURL string
	AccessExpiration, RefreshExpiration                                 time.Duration
	AllowedOrigins                                                      []string
	LogLevel                                                            slog.Level
	Production                                                          bool
}

func env(key, fallback string) string {
	if v, ok := os.LookupEnv(key); ok {
		return v
	}
	return fallback
}
func Load() (Config, error) {
	c := Config{JWTSecret: env("JWT_SECRET", "your-secret-key-change-this-in-production-min-256-bits"),
		ServerPort: env("SERVER_PORT", "8080"), ManagementPort: env("MANAGEMENT_PORT", "7080"),
		FrontendBaseURL: env("APP_FRONTEND_BASE_URL", "http://localhost:5173"),
		Production:      strings.Contains(env("SPRING_PROFILES_ACTIVE", ""), "prod"),
		AllowedOrigins:  strings.Split(env("ALLOWED_ORIGINS", "*"), ",")}
	if c.Production {
		c.ServerPort = env("SERVER_PORT", "8811")
	}
	var err error
	c.DatabaseURL, err = DatabaseURL(env("DB_URL", "jdbc:postgresql://localhost:5432/timetrack"),
		env("DB_USERNAME", "timetrack"), env("DB_PASSWORD", "timetrack"))
	if err != nil {
		return c, err
	}
	for key, dest := range map[string]*time.Duration{"JWT_EXPIRATION": &c.AccessExpiration,
		"JWT_REFRESH_EXPIRATION": &c.RefreshExpiration} {
		fallback := "86400000"
		if key == "JWT_REFRESH_EXPIRATION" {
			fallback = "15552000000"
		}
		ms, e := strconv.ParseInt(env(key, fallback), 10, 64)
		if e != nil {
			return c, fmt.Errorf("%s: %w", key, e)
		}
		*dest = time.Duration(ms) * time.Millisecond
	}
	level := env("LOGGING_LEVEL_ROOT", "INFO")
	appDefault := "DEBUG"
	if c.Production {
		appDefault = env("LOGGING_LEVEL_APP", "INFO")
	}
	level = env("LOGGING_LEVEL_CC_REMER_TIMETRACK", env("LOGGING_LEVEL_ROOT", appDefault))
	switch strings.ToUpper(level) {
	case "TRACE", "DEBUG":
		c.LogLevel = slog.LevelDebug
	case "WARN":
		c.LogLevel = slog.LevelWarn
	case "ERROR", "OFF":
		c.LogLevel = slog.LevelError
	default:
		c.LogLevel = slog.LevelInfo
	}
	if len([]byte(c.JWTSecret)) < 32 {
		return c, fmt.Errorf("JWT_SECRET must contain at least 32 UTF-8 bytes")
	}
	return c, nil
}
func DatabaseURL(raw, username, password string) (string, error) {
	raw = strings.TrimPrefix(raw, "jdbc:")
	u, err := url.Parse(raw)
	if err != nil {
		return "", err
	}
	if u.Scheme != "postgres" && u.Scheme != "postgresql" {
		return "", fmt.Errorf("unsupported DB_URL scheme: %s", u.Scheme)
	}
	if u.User == nil {
		u.User = url.UserPassword(username, password)
	}
	return u.String(), nil
}
