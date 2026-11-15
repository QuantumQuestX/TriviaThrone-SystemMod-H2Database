CREATE TABLE IF NOT EXISTS refresh_token_sessions (
    token_digest VARCHAR(64) PRIMARY KEY,
    username VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    absolute_expiry TIMESTAMP NOT NULL
);
