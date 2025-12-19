-- Add break_minutes column to working_hours table
-- This allows users to specify a standard break duration for each weekday
-- Existing entries will have break_minutes set to 0 (no break)
ALTER TABLE working_hours
    ADD COLUMN break_minutes INTEGER NOT NULL DEFAULT 0;

-- Add check constraint to ensure break_minutes is non-negative
ALTER TABLE working_hours
    ADD CONSTRAINT chk_working_hours_break_minutes
    CHECK (break_minutes >= 0);
