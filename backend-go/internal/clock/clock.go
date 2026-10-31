// Package clock provides the system-default-zone application clock.
package clock

import "time"

type Clock interface{ Now() time.Time }
type System struct{}

func (System) Now() time.Time { return time.Now() }

type Fixed struct{ Time time.Time }

func (c Fixed) Now() time.Time { return c.Time }

// LocalTimestamp preserves local wall time in PostgreSQL TIMESTAMP WITHOUT TIME ZONE.
func LocalTimestamp(t time.Time) time.Time {
	return time.Date(t.Year(), t.Month(), t.Day(), t.Hour(), t.Minute(), t.Second(), t.Nanosecond(), time.UTC)
}
