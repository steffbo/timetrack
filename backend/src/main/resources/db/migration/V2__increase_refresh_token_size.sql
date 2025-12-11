-- Increase refresh_tokens.token column size from VARCHAR(255) to TEXT
-- JWT tokens can easily exceed 255 characters, especially with multiple claims
ALTER TABLE refresh_tokens ALTER COLUMN token TYPE TEXT;
