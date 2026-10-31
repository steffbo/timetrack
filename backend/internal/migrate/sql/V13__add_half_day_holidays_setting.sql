-- Add half_day_holidays_enabled column to users table
-- When enabled, December 24th and 31st count as 0.5 vacation days instead of 1.0
ALTER TABLE users ADD COLUMN half_day_holidays_enabled BOOLEAN NOT NULL DEFAULT FALSE;
