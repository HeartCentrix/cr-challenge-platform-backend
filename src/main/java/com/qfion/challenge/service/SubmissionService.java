package com.qfion.challenge.service;

import com.qfion.challenge.dto.Dto;
import com.qfion.challenge.entity.*;
import com.qfion.challenge.repo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubmissionService {

    private final QuestionRepo questionRepo;
    private final TestcaseRepo testcaseRepo;
    private final CandidateRepo candidateRepo;
    private final AttemptRepo attemptRepo;
    private final AttemptResultRepo attemptResultRepo;
    private final DailyAttemptLockRepo lockRepo;
    private final IdentityService identityService;
    private final Judge0Client judge;
    private final JdbcTemplate jdbc;

    @Value("${challenge.speed-bonus-ratio:0.20}")
    private double speedBonusRatio;

    /** Thrown when an identity has already submitted today. */
    public static class AlreadyPlayedTodayException extends RuntimeException {
        public AlreadyPlayedTodayException(String m) { super(m); }
    }

    public static class QuestionNotFoundException extends RuntimeException {
        public QuestionNotFoundException(String m) { super(m); }
    }

    /** Runs a candidate's code against one sample test case. No data is stored. */
    public Dto.RunResult run(Dto.RunRequest req) {
        Question q = questionRepo.findBySlugAndIsActiveTrue(req.slug())
                .orElseThrow(() -> new QuestionNotFoundException("No active question: " + req.slug()));
        Judge0Client.Execution ex = judge.execute(req.sourceCode(), q.getJudgeLanguageId(), req.stdin(), null);
        return new Dto.RunResult(ex.statusDescription(), ex.stdout(), ex.stderr(), ex.execTimeMs());
    }

    @Transactional
    public Dto.SubmitResponse submit(Dto.SubmitRequest req, String ip, String userAgent) {
        jdbc.query("SELECT pg_advisory_xact_lock_shared(hashtextextended('challenge-daily-reset', 0))",
                (org.springframework.jdbc.core.RowCallbackHandler) row -> { });
        Question q = questionRepo.findBySlugAndIsActiveTrue(req.slug())
                .orElseThrow(() -> new QuestionNotFoundException("No active question: " + req.slug()));

        String emailNorm = identityService.normaliseEmail(req.email());
        String phoneNorm = identityService.normalisePhone(req.phone());
        String emailHash = identityService.hash(emailNorm);
        String phoneHash = identityService.hash(phoneNorm);

        // Serialize overlapping identities across requests and ECS tasks. These locks
        // last until this transaction commits or rolls back, including grading.
        // A waiting submission then sees the committed daily-limit rows rather than
        // racing to insert the same candidate or daily attempt lock.
        Stream.of("challenge-submit:EMAIL:" + emailHash, "challenge-submit:PHONE:" + phoneHash)
                .sorted()
                .forEach(key -> jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
                        (org.springframework.jdbc.core.RowCallbackHandler) row -> { }, key));
        LocalDate today = LocalDate.now();

        if (lockRepo.existsByIdentityTypeAndIdentityHashAndAttemptDate("EMAIL", emailHash, today)
                || lockRepo.existsByIdentityTypeAndIdentityHashAndAttemptDate("PHONE", phoneHash, today)) {
            throw new AlreadyPlayedTodayException("You have already submitted today. Come back tomorrow.");
        }

        Candidate candidate = resolveCandidate(req, emailNorm, phoneNorm);

        List<Testcase> testcases = testcaseRepo.findByQuestionIdOrderByOrdinalAsc(q.getId());
        if (testcases.isEmpty()) {
            // Defence in depth: the schema forbids activating such a question, but never grade against nothing.
            throw new IllegalStateException("Question " + q.getSlug() + " has no test cases and cannot be graded");
        }

        Attempt attempt = attemptRepo.save(Attempt.builder()
                .candidateId(candidate.getId())
                .questionId(q.getId())
                .submittedAt(OffsetDateTime.now())
                .durationMs(req.durationMs())
                .judgeLanguageId(q.getJudgeLanguageId())
                .sourceCode(req.sourceCode())
                .testcasesPassed(0)
                .testcasesTotal(testcases.size())
                .score(BigDecimal.ZERO)
                .speedBonus(BigDecimal.ZERO)
                .ipAddress(ip)
                .userAgent(userAgent)
                .build());

        int passed = 0;
        BigDecimal basePoints = BigDecimal.ZERO;
        String lastStatus = null;

        for (Testcase tc : testcases) {
            Judge0Client.Execution ex = judge.execute(req.sourceCode(), q.getJudgeLanguageId(),
                    tc.getStdin(), tc.getExpectedOutput());
            boolean ok = ex.accepted();
            if (ok) {
                passed++;
                basePoints = basePoints.add(tc.getPoints());
            }
            lastStatus = ex.statusDescription();
            attemptResultRepo.save(AttemptResult.builder()
                    .attemptId(attempt.getId())
                    .testcaseId(tc.getId())
                    .isPassed(ok)
                    .judgeStatus(ex.statusDescription())
                    .execTimeMs(ex.execTimeMs())
                    .memoryKb(ex.memoryKb())
                    .stdoutText(ex.stdout())
                    .build());
        }

        BigDecimal bonus = speedBonus(basePoints, req.durationMs(), q.getTimeLimitSeconds());
        BigDecimal total = basePoints.add(bonus).setScale(2, RoundingMode.HALF_UP);

        attempt.setTestcasesPassed(passed);
        attempt.setScore(total);
        attempt.setSpeedBonus(bonus);
        attempt.setJudgeStatus(lastStatus);
        attemptRepo.save(attempt);

        // Written last so a failed grading run never consumes the candidate's daily attempt.
        lockRepo.save(lock("EMAIL", emailHash, today, candidate.getId(), attempt.getId()));
        lockRepo.save(lock("PHONE", phoneHash, today, candidate.getId(), attempt.getId()));

        log.info("Attempt {} by candidate {} on {}: {}/{} test cases, score {}",
                attempt.getId(), candidate.getId(), q.getSlug(), passed, testcases.size(), total);

        return new Dto.SubmitResponse("Your submission has been saved.");
    }

    private DailyAttemptLock lock(String type, String hash, LocalDate date, Long candidateId, Long attemptId) {
        return DailyAttemptLock.builder()
                .identityType(type).identityHash(hash).attemptDate(date)
                .candidateId(candidateId).attemptId(attemptId)
                .createdAt(OffsetDateTime.now())
                .build();
    }

    /** Up to speedBonusRatio of base points, scaled by how much of the time limit was left. */
    private BigDecimal speedBonus(BigDecimal basePoints, Long durationMs, Integer limitSeconds) {
        if (durationMs == null || limitSeconds == null || limitSeconds <= 0 || basePoints.signum() == 0) {
            return BigDecimal.ZERO;
        }
        double used = durationMs / 1000.0 / limitSeconds;
        double remaining = Math.max(0.0, Math.min(1.0, 1.0 - used));
        return basePoints.multiply(BigDecimal.valueOf(speedBonusRatio * remaining)).setScale(2, RoundingMode.HALF_UP);
    }

    private Candidate resolveCandidate(Dto.SubmitRequest req, String emailNorm, String phoneNorm) {
        Candidate c = candidateRepo.findByEmailNormalised(emailNorm)
                .or(() -> candidateRepo.findByPhoneNormalised(phoneNorm))
                .orElse(null);
        if (c == null) {
            c = Candidate.builder()
                    .fullName(req.fullName())
                    .emailRaw(req.email()).emailNormalised(emailNorm)
                    .phoneRaw(req.phone()).phoneNormalised(phoneNorm)
                    .isConsented(Boolean.TRUE.equals(req.consent()))
                    .sourceCampaign(req.sourceCampaign())
                    .firstSeenAt(OffsetDateTime.now())
                    .lastSeenAt(OffsetDateTime.now())
                    .build();
        } else {
            c.setFullName(req.fullName());
            c.setLastSeenAt(OffsetDateTime.now());
            if (Boolean.TRUE.equals(req.consent())) c.setIsConsented(true);
        }
        return candidateRepo.save(c);
    }
}
