-- Requires migration 14. Preserve all questions, snapshots and submitted answers.
BEGIN;
DO $$ BEGIN IF current_database()<>'challenge_platform' THEN RAISE EXCEPTION 'Wrong database'; END IF; END $$;
ALTER TABLE challenge_platform.session_followup DROP CONSTRAINT session_followup_review_status_check;
ALTER TABLE challenge_platform.session_followup ADD CONSTRAINT session_followup_review_status_check
 CHECK(review_status IN ('UNANSWERED','SKIPPED','CORRECT','INCORRECT','NEEDS_REVIEW','QUEUED','GRADING_UNAVAILABLE'));
COMMIT;
