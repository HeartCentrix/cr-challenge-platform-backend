package com.qfion.challenge.dto;

import jakarta.validation.constraints.*;
import java.util.List;

/** All request and response shapes for the public API. */
public final class Dto {

    private Dto() {}

    public record QuestionSummary(Long id, String slug, String title, Integer difficulty,
                                  String language, Integer timeLimitSeconds) {}

    public record SampleTestcase(String stdin, String expectedOutput) {}

    public record QuestionDetail(Long id, String slug, String title, String prompt, Integer difficulty,
                                 String language, Integer judgeLanguageId, String starterCode,
                                 Integer timeLimitSeconds, List<SampleTestcase> samples) {}

    public record RunRequest(@NotBlank String slug, @NotBlank String sourceCode, String stdin) {}

    public record RunResult(String status, String stdout, String stderr, Integer execTimeMs) {}

    public record SubmitRequest(
            @NotBlank String slug,
            @NotBlank String sourceCode,
            @NotBlank @Size(max = 150) String fullName,
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 7, max = 32) String phone,
            @AssertTrue(message = "Consent is required") Boolean consent,
            Long durationMs,
            String sourceCampaign) {}

    public record TestcaseOutcome(int ordinal, boolean passed, String status, Integer execTimeMs) {}

    public record SubmitResponse(Long attemptId, int testcasesPassed, int testcasesTotal,
                                 double score, double speedBonus, Long durationMs,
                                 List<TestcaseOutcome> results) {}

    public record LeaderboardRow(long rank, String displayName, double totalScore,
                                 long testcasesCleared, long questionsSolved, Long totalTimeMs) {}
}
