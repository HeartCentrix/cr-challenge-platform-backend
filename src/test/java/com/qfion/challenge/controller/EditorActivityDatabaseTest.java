package com.qfion.challenge.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qfion.challenge.dto.Dto;
import com.qfion.challenge.service.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Local-only submission/storage/admin round trip. Every fixture rolls back; judge is mocked. */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "CHALLENGE_STATS_DB_TESTS", matches = "true")
@Transactional(isolation = Isolation.REPEATABLE_READ)
class EditorActivityDatabaseTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired SubmissionService submissions;
    @Autowired AdminStatsService stats;
    @Autowired EntityManager em;
    @Autowired ActivityCheckpointService checkpoints;
    @MockBean Judge0Client judge;

    @Test void persistsActivityWithItsQuestionAndCandidateAndReadsItOnlyThroughOwnedAttempt() throws Exception {
        assertEquals("challenge_platform", jdbc.queryForObject("SELECT current_database()", String.class));
        assertTrue(java.util.List.of("127.0.0.1", "::1").contains(jdbc.queryForObject("SELECT host(inet_server_addr())", String.class)));
        String slug = jdbc.queryForObject("SELECT slug FROM challenge_platform.question WHERE is_active ORDER BY id LIMIT 1", String.class);
        var telemetry = new ObjectMapper().readValue(EditorActivityTest.ACTIVITY.replace("test-question", slug), Dto.EditorActivity.class);
        String email = "activity-test-" + UUID.randomUUID() + "@example.invalid";
        String phone = "+1202" + String.format("%07d", Math.floorMod(UUID.randomUUID().hashCode(), 10000000));
        when(judge.execute(anyString(), anyInt(), nullable(String.class), nullable(String.class)))
                .thenReturn(new Judge0Client.Execution(3, "Accepted", "", null, 1, 1));
        String token = UUID.randomUUID().toString();
        var checkpoint = new Dto.ActivityCheckpointRequest(token, 1, slug, "class Main {}", telemetry);
        checkpoints.save(checkpoint);
        checkpoints.save(checkpoint); // A retried acknowledgement must not duplicate the row.
        var req = new Dto.SubmitRequest(slug, "class Main {}", "Activity Fixture", email, phone, false, 1000L, "test", telemetry, token);
        assertNotNull(submissions.submit(req, "127.0.0.1", "local-test"));
        em.flush();
        long candidate = jdbc.queryForObject("SELECT id FROM challenge_platform.candidate WHERE email_normalised = ?", Long.class, email);
        long attempt = jdbc.queryForObject("SELECT id FROM challenge_platform.attempt WHERE candidate_id = ?", Long.class, candidate);
        var saved = stats.attempt(candidate, attempt);
        assertEquals(slug, saved.summary().slug());
        assertEquals(telemetry, saved.editorActivity());
        assertEquals(1, saved.checkpointHistory().checkpoints().size());
        assertTrue(saved.checkpointHistory().finalCodeMatches());
        assertFalse(saved.checkpointHistory().reportingGaps());
        assertFalse(new ObjectMapper().findAndRegisterModules().writeValueAsString(saved).contains(token));
        assertThrows(ResponseStatusException.class, () -> checkpoints.save(checkpoint)); // Sealed by submission.
        assertThrows(ResponseStatusException.class, () -> stats.attempt(-1, attempt));
    }

    @Test void checkpointWritesAreOrderedImmutableBoundedAndExpire() throws Exception {
        assertEquals("challenge_platform", jdbc.queryForObject("SELECT current_database()", String.class));
        assertTrue(java.util.List.of("127.0.0.1", "::1").contains(jdbc.queryForObject("SELECT host(inet_server_addr())", String.class)));
        String slug = jdbc.queryForObject("SELECT slug FROM challenge_platform.question WHERE is_active ORDER BY id LIMIT 1", String.class);
        var telemetry = new ObjectMapper().readValue(EditorActivityTest.ACTIVITY.replace("test-question", slug), Dto.EditorActivity.class);
        String token = UUID.randomUUID().toString();
        var first = new Dto.ActivityCheckpointRequest(token, 1, slug, "first", telemetry);
        checkpoints.save(first);
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> checkpoints.save(new Dto.ActivityCheckpointRequest(token, 1, slug, "overwrite", telemetry))).getStatusCode().value());
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> checkpoints.save(new Dto.ActivityCheckpointRequest(token, 3, slug, "third", telemetry))).getStatusCode().value());
        assertEquals(429, assertThrows(ResponseStatusException.class, () -> checkpoints.save(new Dto.ActivityCheckpointRequest(token, 2, slug, "second", telemetry))).getStatusCode().value());
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> checkpoints.save(new Dto.ActivityCheckpointRequest(UUID.randomUUID().toString(), 2, slug, "other", telemetry))).getStatusCode().value());
        assertEquals("not-recorded", checkpoints.history(-1, "anything").status());
        String hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        jdbc.update("UPDATE challenge_platform.activity_session SET created_at = now() - interval '2 hours' WHERE token_hash = ?", hash);
        assertEquals(410, assertThrows(ResponseStatusException.class, () -> checkpoints.save(first)).getStatusCode().value());
    }
}
