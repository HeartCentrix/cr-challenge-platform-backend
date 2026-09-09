package com.qfion.challenge.dto;

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
    public record CandidateRow(long id, String fullName, String email, String phone, String sourceCampaign, Performance performance) {}
    public record CandidateDetail(long id, String fullName, String email, String phone, boolean consented,
            String sourceCampaign, OffsetDateTime firstSeenAt, OffsetDateTime lastSeenAt,
            Performance performance, Page<AttemptSummary> attempts) {}
    public record CaseResult(long id, int ordinal, boolean sample, String stdin, String expectedOutput,
            Boolean passed, String judgeStatus, Integer execTimeMs, Integer memoryKb, String stdout) {}
    public record AttemptDetail(AttemptSummary summary, String sourceCode, String prompt, int difficulty,
            int timeLimitSeconds, String starterCode, String referenceSolution, String ipAddress,
            String userAgent, List<CaseResult> testcases, Dto.EditorActivity editorActivity,
            com.qfion.challenge.service.ActivityCheckpointService.History checkpointHistory) {}
}
