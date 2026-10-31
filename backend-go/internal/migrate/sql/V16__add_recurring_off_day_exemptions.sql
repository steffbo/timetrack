-- Add exemptions table for recurring off-days
-- Allows specific dates to be exempted from recurring off-day patterns,
-- making them regular working days instead

CREATE TABLE recurring_off_day_exemptions (
    id BIGSERIAL PRIMARY KEY,
    recurring_off_day_id BIGINT NOT NULL,
    exemption_date DATE NOT NULL,
    reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    
    CONSTRAINT fk_exemption_recurring_off_day 
        FOREIGN KEY (recurring_off_day_id) 
        REFERENCES recurring_off_days(id) 
        ON DELETE CASCADE,
    
    -- Prevent duplicate exemptions for the same date on the same rule
    CONSTRAINT uq_exemption_date_per_rule 
        UNIQUE (recurring_off_day_id, exemption_date)
);

-- Index for efficient lookups by recurring off-day
CREATE INDEX idx_exemptions_recurring_off_day_id ON recurring_off_day_exemptions(recurring_off_day_id);

-- Index for date-based lookups
CREATE INDEX idx_exemptions_date ON recurring_off_day_exemptions(exemption_date);
