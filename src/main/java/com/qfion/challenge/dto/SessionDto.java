package com.qfion.challenge.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;

public final class SessionDto {
    private SessionDto() {}
    public record Start(@NotNull @Pattern(regexp="[0-9a-fA-F-]{36}") String token,
        @NotBlank @Size(max=150) String fullName, @NotBlank @Email @Size(max=255) String email,
        @NotBlank @Size(min=7,max=32) String phone, @Size(max=255) String sourceCampaign) {}
    public record Access(@NotNull @Pattern(regexp="[0-9a-fA-F-]{36}") String token) {}
    public record Answer(@NotNull @Pattern(regexp="[0-9a-fA-F-]{36}") String token,
        @Min(1) int ordinal, @NotBlank @Size(max=100000) String sourceCode,
        @Valid Dto.EditorActivity editorActivity) {}
    public record Draft(@NotNull @Pattern(regexp="[0-9a-fA-F-]{36}") String token,
        @Min(1) int ordinal, @Min(1) @Max(100000) int revision,
        @NotNull @Size(max=100000) String sourceCode, @Valid Dto.EditorActivity editorActivity) {}
    /** Never includes score, hidden tests, candidate details, or a bearer token. */
    public record State(String status, String reason, OffsetDateTime serverNow, OffsetDateTime startedAt,
        OffsetDateTime expiresAt, OffsetDateTime finishedAt, int submittedAnswers, int ordinal,
        Dto.QuestionDetail question, String draftCode, int draftRevision, boolean restartAllowed) {}
}
