CREATE TABLE invite_tokens
(
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    token      TEXT        NOT NULL UNIQUE,
    expires_at TIMESTAMP   NOT NULL,
    created_at TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE INDEX idx_invite_tokens_token ON invite_tokens (token);
CREATE INDEX idx_invite_tokens_expires_at ON invite_tokens (expires_at);

ALTER TABLE users
    ALTER COLUMN password_hash DROP NOT NULL;
