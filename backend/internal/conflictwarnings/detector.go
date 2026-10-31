package conflictwarnings

import (
	"context"
	"time"

	"github.com/steffbo/timetrack/backend/internal/clock"
	"github.com/steffbo/timetrack/backend/internal/db"
	"github.com/steffbo/timetrack/backend/internal/recurringoffdays"
)

// TimeEntryRef carries the time entry fields the detector needs.
type TimeEntryRef struct {
	ID, UserID int64
	EntryDate  time.Time
}

// Detector ports RecurringOffDayConflictDetector. Callers pass the transaction (or pool) as q.
type Detector struct{ Clock clock.Clock }

// DetectAndCreateIfNeeded returns the existing or newly created warning, or nil when there is no conflict.
func (d Detector) DetectAndCreateIfNeeded(ctx context.Context, q db.Querier, e TimeEntryRef) (*Warning, error) {
	if e.UserID == 0 || e.EntryDate.IsZero() {
		return nil, nil
	}
	existing, err := FindByUserIDAndConflictDate(ctx, q, e.UserID, e.EntryDate)
	if err != nil || existing != nil {
		return existing, err
	}
	active, err := recurringoffdays.FindActiveByUserIDAndDate(ctx, q, e.UserID, e.EntryDate)
	if err != nil {
		return nil, err
	}
	evaluator := recurringoffdays.Evaluator{Q: q}
	for i := range active {
		applies, err := evaluator.AppliesToDateWithExemptions(ctx, &active[i], e.EntryDate)
		if err != nil {
			return nil, err
		}
		if applies {
			id, ruleID := e.ID, active[i].ID
			w := &Warning{UserID: e.UserID, ConflictDate: e.EntryDate, TimeEntryID: &id, RecurringOffDayID: &ruleID}
			return w, Insert(ctx, q, w, clock.LocalTimestamp(d.Clock.Now()))
		}
	}
	return nil, nil
}

// CleanupForTimeEntry deletes the warnings of a time entry; errors are ignored by design.
func (d Detector) CleanupForTimeEntry(ctx context.Context, q db.Querier, timeEntryID int64) {
	if timeEntryID != 0 {
		_ = DeleteByTimeEntryID(ctx, q, timeEntryID)
	}
}

// Reevaluate runs the detection for each entry.
func (d Detector) Reevaluate(ctx context.Context, q db.Querier, userID int64, entries []TimeEntryRef) error {
	if userID == 0 {
		return nil
	}
	for _, e := range entries {
		if _, err := d.DetectAndCreateIfNeeded(ctx, q, e); err != nil {
			return err
		}
	}
	return nil
}
