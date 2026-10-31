package main

import (
	"context"
	"errors"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/steffbo/timetrack/backend-go/internal/clock"
	"github.com/steffbo/timetrack/backend-go/internal/config"
	"github.com/steffbo/timetrack/backend-go/internal/db"
	"github.com/steffbo/timetrack/backend-go/internal/migrate"
	"github.com/steffbo/timetrack/backend-go/internal/server"
)

func main() {
	if err := run(); err != nil {
		slog.Error("Application stopped", "error", err)
		os.Exit(1)
	}
}
func run() error {
	c, err := config.Load()
	if err != nil {
		return err
	}
	slog.SetDefault(slog.New(slog.NewTextHandler(os.Stdout, &slog.HandlerOptions{Level: c.LogLevel})))
	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGTERM, syscall.SIGINT)
	defer stop()
	pool, err := db.Open(ctx, c.DatabaseURL)
	if err != nil {
		return err
	}
	defer pool.Close()
	if err = migrate.Run(ctx, pool); err != nil {
		return err
	}
	app := server.New(pool, c, clock.System{})
	api := &http.Server{Addr: ":" + c.ServerPort, Handler: app.Router()}
	management := &http.Server{Addr: ":" + c.ManagementPort, Handler: app.ManagementRouter()}
	failures := make(chan error, 2)
	for _, srv := range []*http.Server{api, management} {
		go func() {
			slog.Info("HTTP server listening", "address", srv.Addr)
			failures <- srv.ListenAndServe()
		}()
	}
	select {
	case <-ctx.Done():
	case err = <-failures:
	}
	// No Spring shutdown timeout is configured; allow in-flight requests to drain.
	shutdown, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()
	e1 := api.Shutdown(shutdown)
	e2 := management.Shutdown(shutdown)
	if errors.Is(err, http.ErrServerClosed) {
		err = nil
	}
	return errors.Join(err, e1, e2)
}
