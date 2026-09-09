package com.qfion.challenge.dto;

import jakarta.validation.constraints.*;
import jakarta.validation.Valid;
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
            @NotNull Boolean consent,
            Long durationMs,
            String sourceCampaign,
            @Valid EditorActivity editorActivity,
            @Pattern(regexp = "[0-9a-fA-F-]{36}") String activityToken) {
        public SubmitRequest(String slug, String sourceCode, String fullName, String email, String phone,
                Boolean consent, Long durationMs, String sourceCampaign, EditorActivity editorActivity) {
            this(slug, sourceCode, fullName, email, phone, consent, durationMs, sourceCampaign, editorActivity, null);
        }
    }

    public record ActivityCheckpointRequest(
            @NotNull @Pattern(regexp = "[0-9a-fA-F-]{36}") String token,
            @Min(1) @Max(120) int sequence,
            @NotBlank @Size(max = 255) String slug,
            @NotNull @Size(max = 32000) String sourceCode,
            @NotNull @Valid EditorActivity activity) {}

    public record ClipboardCounts(
            @Min(0) @Max(1000000) int copy, @Min(0) @Max(1000000) int cut,
            @Min(0) @Max(1000000) int paste, @Min(0) @Max(1000000) int drop) {}

    public record ActivityEvent(
            @Min(0) @Max(86400000) long offsetMs,
            @NotNull @Pattern(regexp = "key-(character|delete|layout|navigation|shortcut)|composition|model-change|bulk-change|bulk-unexplained|unexplained-change|unobserved-change|undo|redo|(copy|cut|paste|drop)-blocked|paste-observed") String kind,
            @NotNull @Pattern(regexp = "question|answer") String area,
            Boolean trusted, @Min(0) @Max(10000000) Integer inserted, @Min(0) @Max(10000000) Integer deleted) {}

    /** Unverified client observations. Missing telemetry is not a zero count. */
    public record EditorActivity(
            @Min(1) @Max(1) int version,
            @NotBlank @Size(max = 255) String questionSlug,
            @NotBlank @Size(max = 40) String startedAt,
            @Min(0) @Max(86400000) long elapsedMs,
            @NotNull @Valid ClipboardCounts question, @NotNull @Valid ClipboardCounts answer,
            @Min(0) @Max(1000000) int keydownCount,
            @Min(0) @Max(1000000) int trustedKeydownCount,
            @Min(0) @Max(1000000) int syntheticEvents,
            @Min(0) @Max(1000000) int modelChangeCount,
            @Min(0) @Max(1000000) int unexplainedChangeCount,
            @Min(0) @Max(1000000) int observedPasteCount,
            @Min(0) @Max(10000000) long insertedCharacters,
            @Min(0) @Max(10000000) long deletedCharacters,
            @Min(0) @Max(1000000) int droppedEvents,
            @NotNull @Size(max = 5000) List<@NotNull @Valid ActivityEvent> events,
            @Min(0) @Max(1000000) Integer bulkChangeCount,
            @Min(0) @Max(1000000) Integer unexplainedBulkChangeCount,
            @Min(0) @Max(10000000) Integer largestInsertion) {}

    /** Candidate-facing acknowledgement; grading data remains in the database. */
    public record SubmitResponse(String message) {}

    public record LeaderboardRow(long rank, String displayName) {}
}
