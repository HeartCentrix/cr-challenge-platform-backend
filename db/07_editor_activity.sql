-- Run as challenge database owner BEFORE starting/deploying the updated backend.
-- Additive migration; historical submissions remain NULL (not recorded).
BEGIN;
DO $$ BEGIN
    IF current_database() <> 'challenge_platform' THEN
        RAISE EXCEPTION 'This migration is only for the challenge_platform database';
    END IF;
END $$;
ALTER TABLE challenge_platform.attempt ADD COLUMN IF NOT EXISTS editor_activity_json TEXT
    CHECK (editor_activity_json IS NULL OR
        (octet_length(editor_activity_json) <= 1000000 AND jsonb_typeof(editor_activity_json::jsonb) = 'object'));
COMMENT ON COLUMN challenge_platform.attempt.editor_activity_json IS
    'Unverified client editor activity per submitted answer; NULL means not recorded. No literal keys or clipboard contents. Deleted with its attempt.';
COMMIT;
