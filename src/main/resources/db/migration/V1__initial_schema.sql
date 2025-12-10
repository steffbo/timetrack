-- Create users table
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('ADMIN', 'USER')),
    active BOOLEAN DEFAULT true NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Create working_hours table
CREATE TABLE working_hours (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    weekday SMALLINT NOT NULL CHECK (weekday BETWEEN 1 AND 7),
    hours DECIMAL(4,2) NOT NULL CHECK (hours >= 0 AND hours <= 24),
    is_working_day BOOLEAN DEFAULT true NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_working_hours_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uk_user_weekday UNIQUE (user_id, weekday)
);

-- Create time_entries table
CREATE TABLE time_entries (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    entry_date DATE NOT NULL,
    clock_in TIMESTAMP NOT NULL,
    clock_out TIMESTAMP,
    entry_type VARCHAR(20) DEFAULT 'WORK' NOT NULL CHECK (entry_type IN ('WORK', 'SICK', 'PTO', 'EVENT')),
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_time_entries_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_clock_out_after_clock_in CHECK (clock_out IS NULL OR clock_out > clock_in)
);

-- Create refresh_tokens table
CREATE TABLE refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token VARCHAR(255) UNIQUE NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Create indexes for performance
CREATE INDEX idx_time_entries_user_date ON time_entries(user_id, entry_date);
CREATE INDEX idx_time_entries_clock_in ON time_entries(clock_in);
CREATE INDEX idx_time_entries_user_id ON time_entries(user_id);
CREATE INDEX idx_working_hours_user_id ON working_hours(user_id);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_token ON refresh_tokens(token);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens(expires_at);

-- Insert default admin user (password: admin)
-- Password hash is bcrypt hash of "admin" with strength 10
INSERT INTO users (email, password_hash, first_name, last_name, role, active)
VALUES ('admin@timetrack.local', '$2a$10$N9qo8uLOickgx2ZMRZoMye.IjefVqMpjYZx3qRzPvr9qGVo8xOZZS', 'Admin', 'User', 'ADMIN', true);

-- Insert default working hours for admin (Monday-Friday: 8 hours, Weekend: 0 hours)
INSERT INTO working_hours (user_id, weekday, hours, is_working_day)
VALUES
    (1, 1, 8.00, true),  -- Monday
    (1, 2, 8.00, true),  -- Tuesday
    (1, 3, 8.00, true),  -- Wednesday
    (1, 4, 8.00, true),  -- Thursday
    (1, 5, 8.00, true),  -- Friday
    (1, 6, 0.00, false), -- Saturday
    (1, 7, 0.00, false); -- Sunday
