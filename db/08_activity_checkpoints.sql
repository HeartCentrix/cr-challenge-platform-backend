-- Run as the challenge database owner before deploying this backend.
BEGIN;
DO $$ BEGIN
    IF current_database() <> 'challenge_platform' THEN
        RAISE EXCEPTION 'This migration is only for the challenge_platform database';
    END IF;
END $$;
CREATE TABLE IF NOT EXISTS challenge_platform.activity_session (
    token_hash VARCHAR(64) PRIMARY KEY,
    question_slug VARCHAR(255) NOT NULL REFERENCES challenge_platform.question(slug),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    attempt_id BIGINT UNIQUE REFERENCES challenge_platform.attempt(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS activity_session_expiry ON challenge_platform.activity_session(created_at) WHERE attempt_id IS NULL;
CREATE TABLE IF NOT EXISTS challenge_platform.activity_checkpoint (
    token_hash VARCHAR(64) NOT NULL REFERENCES challenge_platform.activity_session(token_hash) ON DELETE CASCADE,
    sequence INTEGER NOT NULL CHECK (sequence BETWEEN 1 AND 120),
    received_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    source_code TEXT NOT NULL CHECK (length(source_code) <= 32000),
    activity_json TEXT NOT NULL CHECK (octet_length(activity_json) <= 80000),
    PRIMARY KEY (token_hash, sequence)
);
GRANT SELECT, INSERT, UPDATE, DELETE ON challenge_platform.activity_session, challenge_platform.activity_checkpoint TO challenge_app;
COMMENT ON TABLE challenge_platform.activity_checkpoint IS 'Append-only client-reported editor checkpoints. Server receipt time is authoritative; content is not. No console/clipboard contents or personal-field tracking.';
COMMIT;
