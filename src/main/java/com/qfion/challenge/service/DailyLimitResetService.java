package com.qfion.challenge.service;

import com.qfion.challenge.repo.CandidateRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailyLimitResetService {
    private final JdbcTemplate jdbc;
    private final CandidateRepo candidates;
    private final IdentityService identities;

    public record EmailResult(String email, String status, int locksRemoved) {}
    public record ResetResult(LocalDate date, List<EmailResult> results) {}

    @Transactional
    public ResetResult reset(List<String> emails, String adminEmail) {
        // Exclusive counterpart to the shared gate taken by submissions. An in-flight
        // submission must finish before reset; a subsequent submission can use the new slot.
        jdbc.execute("SET LOCAL lock_timeout = '10s'");
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended('challenge-daily-reset', 0))",
                (RowCallbackHandler) row -> { });
        LocalDate today = LocalDate.now(); // Same server calendar as SubmissionService.
        var unique = new LinkedHashMap<String, String>();
        emails.forEach(email -> unique.putIfAbsent(identities.normaliseEmail(email), email.trim()));
        var results = new ArrayList<EmailResult>();
        unique.forEach((normalised, input) -> {
            var candidate = candidates.findByEmailNormalised(normalised);
            if (candidate.isEmpty()) {
                results.add(new EmailResult(input, "NOT_FOUND", 0));
                return;
            }
            long id = candidate.get().getId();
            // Revoke the old session before granting another slot; its timer must not continue.
            jdbc.update("UPDATE challenge_platform.challenge_session SET finished_at=clock_timestamp(),finish_reason='RESET' WHERE candidate_id=? AND session_date=? AND finished_at IS NULL", id, today);
            // Remove both identity locks, including aliases previously used by this candidate.
            // Never remove attempts, results, candidates, or another day's locks.
            int removed = jdbc.update("DELETE FROM challenge_platform.daily_attempt_lock "
                    + "WHERE candidate_id = ? AND attempt_date = ?", id, today);
            jdbc.update("INSERT INTO challenge_platform_admin.daily_limit_reset_audit "
                    + "(admin_email, reset_date, candidate_id, locks_removed) VALUES (?, ?, ?, ?)",
                    adminEmail, today, id, removed);
            results.add(new EmailResult(input, removed > 0 ? "RESET" : "NO_LIMIT", removed));
            log.info("Admin daily-limit reset candidateId={} date={} locksRemoved={}", id, today, removed);
        });
        return new ResetResult(today, results);
    }
}
