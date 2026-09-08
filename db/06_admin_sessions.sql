-- Run as database owner after 04_admin_schema.sql. No seeded credentials.
BEGIN;
CREATE TABLE IF NOT EXISTS challenge_platform_admin.admin_session (
    token_hash VARCHAR(64) PRIMARY KEY,
    admin_email VARCHAR(255) NOT NULL REFERENCES challenge_platform_admin.admin_user(email),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_admin_session_expiry ON challenge_platform_admin.admin_session(expires_at);
GRANT SELECT, INSERT, DELETE ON challenge_platform_admin.admin_session TO challenge_app;
COMMIT;
