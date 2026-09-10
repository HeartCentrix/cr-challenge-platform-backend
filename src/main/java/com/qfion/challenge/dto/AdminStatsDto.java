package com.qfion.challenge.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.qfion.challenge.service.AiSourceMarker;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/** Private, authenticated admin responses. Never reuse these in public endpoints. */
public final class AdminStatsDto {
    private AdminStatsDto() {}
    public record Bucket(String key, String label, long count, double candidatePercentage) {}
    public record Overview(long totalCandidates, long totalAttempts, String basis, BigDecimal averageCandidateScore, List<Bucket> buckets) {}
    public record CandidatePage(List<CandidateRow> items, long total, Long nextCursor) {}
    public record Performance(long attemptCount, long questionsAttempted, long testcasesPassed, long testcasesTotal,
            BigDecimal passPercentage, BigDecimal totalScore, BigDecimal averageScore, Long durationMs, OffsetDateTime lastSubmittedAt) {}
    public record Page<T>(List<T> items, long total, int page, int size) {}
    public record AttemptSummary(long id, long questionId, String slug, String title, String language,
            OffsetDateTime submittedAt, Long durationMs, int testcasesPassed, int testcasesTotal,
            BigDecimal passPercentage, BigDecimal score, BigDecimal speedBonus, String judgeStatus) {}
    public record CandidateRow(long id, String fullName, String email, String phone, String sourceCampaign, Performance performance,
            String regionCode, String region, boolean aiMarkerDetected) {}
    public record CandidateDetail(long id, String fullName, String email, String phone, boolean consented,
            String sourceCampaign, OffsetDateTime firstSeenAt, OffsetDateTime lastSeenAt,
            Performance performance, Page<AttemptSummary> attempts, AttemptDayNavigation history) {}
    public record AttemptDayNavigation(String day, String previousDay, String nextDay, String asOf) {}
    public record CaseResult(long id, int ordinal, boolean sample, String stdin, String expectedOutput,
            Boolean passed, String judgeStatus, Integer execTimeMs, Integer memoryKb, String stdout) {}
    public record AttemptDetail(AttemptSummary summary, String sourceCode, String prompt, int difficulty,
            int timeLimitSeconds, String starterCode, String referenceSolution, String ipAddress,
            String userAgent, List<CaseResult> testcases, Dto.EditorActivity editorActivity,
            com.qfion.challenge.service.ActivityCheckpointService.History checkpointHistory, SessionTiming sessionTiming) {
        /** Derived from the saved answer, never a client-supplied detection flag. */
        @JsonProperty("aiMarkerDetected")
        public boolean aiMarkerDetected() { return AiSourceMarker.detected(sourceCode); }
    }
    public record SessionTiming(long sessionId, int questionNumber, OffsetDateTime startedAt,
            OffsetDateTime expiresAt, OffsetDateTime finishedAt, Long elapsedMs) {}
}
