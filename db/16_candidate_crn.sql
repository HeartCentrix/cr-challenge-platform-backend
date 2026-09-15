-- Independent of follow-up migrations 12-15; safe for the current dev backend.
BEGIN;
DO $$ BEGIN IF current_database()<>'challenge_platform' THEN RAISE EXCEPTION 'Wrong database'; END IF; END $$;
CREATE TABLE IF NOT EXISTS challenge_platform.candidate_crn_match (
 candidate_id bigint PRIMARY KEY REFERENCES challenge_platform.candidate(id) ON DELETE CASCADE,
 version bigint NOT NULL DEFAULT 1,
 status varchar(16) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','CHECKED','ERROR')),
 email_match boolean,
 phone_match boolean,
 checked_at timestamptz,
 requested_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 attempts integer NOT NULL DEFAULT 0,
 next_attempt_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 CHECK ((status='CHECKED' AND email_match IS NOT NULL AND phone_match IS NOT NULL AND checked_at IS NOT NULL)
     OR (status<>'CHECKED' AND email_match IS NULL AND phone_match IS NULL AND checked_at IS NULL))
);
REVOKE ALL ON challenge_platform.candidate_crn_match FROM PUBLIC,challenge_app;
GRANT SELECT ON challenge_platform.candidate_crn_match TO challenge_app;
CREATE INDEX IF NOT EXISTS candidate_crn_pending ON challenge_platform.candidate_crn_match(next_attempt_at)
 WHERE status<>'CHECKED' AND attempts<5;

-- Durable request in the same transaction as the candidate. Never makes network calls.
CREATE OR REPLACE FUNCTION challenge_platform.request_candidate_crn() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
BEGIN
 INSERT INTO challenge_platform.candidate_crn_match(candidate_id) VALUES(NEW.id)
 ON CONFLICT(candidate_id) DO UPDATE SET version=challenge_platform.candidate_crn_match.version+1,
 status='PENDING',email_match=NULL,phone_match=NULL,checked_at=NULL,attempts=0,
 requested_at=clock_timestamp(),next_attempt_at=clock_timestamp();
 RETURN NEW;
END $$;
REVOKE ALL ON FUNCTION challenge_platform.request_candidate_crn() FROM PUBLIC;
DROP TRIGGER IF EXISTS candidate_crn_requested ON challenge_platform.candidate;
CREATE TRIGGER candidate_crn_requested AFTER INSERT ON challenge_platform.candidate
 FOR EACH ROW EXECUTE FUNCTION challenge_platform.request_candidate_crn();
DROP TRIGGER IF EXISTS candidate_crn_identity_changed ON challenge_platform.candidate;
CREATE TRIGGER candidate_crn_identity_changed AFTER UPDATE OF email_raw,phone_raw ON challenge_platform.candidate
 FOR EACH ROW WHEN (OLD.email_raw IS DISTINCT FROM NEW.email_raw OR OLD.phone_raw IS DISTINCT FROM NEW.phone_raw)
 EXECUTE FUNCTION challenge_platform.request_candidate_crn();
COMMIT;
