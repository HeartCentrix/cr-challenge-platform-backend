-- Owner migration, after 09. Existing IPs are resolved locally by the bounded backfill worker.
BEGIN;
DO $$ BEGIN
  IF current_database() <> 'challenge_platform' THEN RAISE EXCEPTION 'Wrong database'; END IF;
END $$;
ALTER TABLE challenge_platform.attempt ADD COLUMN IF NOT EXISTS region_code VARCHAR(16);
CREATE INDEX IF NOT EXISTS attempt_region_pending ON challenge_platform.attempt(id) WHERE region_code IS NULL;
COMMIT;
