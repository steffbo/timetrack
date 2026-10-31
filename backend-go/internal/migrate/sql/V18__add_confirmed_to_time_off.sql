-- Add confirmed column to time_off table
ALTER TABLE time_off 
ADD COLUMN confirmed BOOLEAN NOT NULL DEFAULT FALSE;

-- Add index for querying unconfirmed time-offs
CREATE INDEX idx_time_off_confirmed ON time_off(user_id, confirmed);
