package config

import (
	"net/url"
	"testing"
	"time"
)

func TestDatabaseURL(t *testing.T) {
	for _, raw := range []string{"jdbc:postgresql://host:5432/db?sslmode=require", "postgres://host:5432/db?sslmode=require"} {
		dsn, err := DatabaseURL(raw, "name@host", "p:a/ss")
		if err != nil {
			t.Fatal(err)
		}
		u, err := url.Parse(dsn)
		if err != nil {
			t.Fatal(err)
		}
		password, _ := u.User.Password()
		if u.User.Username() != "name@host" || password != "p:a/ss" || u.Query().Get("sslmode") != "require" {
			t.Fatal(dsn)
		}
	}
}
func TestDefaultsAndOverrides(t *testing.T) {
	t.Setenv("SPRING_PROFILES_ACTIVE", "prod")
	c, err := Load()
	if err != nil {
		t.Fatal(err)
	}
	if c.ServerPort != "8811" || c.ManagementPort != "7080" || c.AccessExpiration != 24*time.Hour ||
		c.RefreshExpiration != 180*24*time.Hour {
		t.Fatal(c)
	}
	t.Setenv("JWT_EXPIRATION", "1234")
	t.Setenv("JWT_REFRESH_EXPIRATION", "5678")
	c, err = Load()
	if err != nil || c.AccessExpiration != 1234*time.Millisecond || c.RefreshExpiration != 5678*time.Millisecond {
		t.Fatal(c, err)
	}
}
