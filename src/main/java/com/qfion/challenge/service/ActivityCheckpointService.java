package com.qfion.challenge.service;

import com.qfion.challenge.dto.Dto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityCheckpointService {
    private final JdbcTemplate jdbc;
    private final Map<String, long[]> limits = new HashMap<>();
    public record Checkpoint(int sequence, OffsetDateTime receivedAt, String sourceCode, Dto.EditorActivity activity) {}
    public record History(String status, Boolean finalCodeMatches, boolean reportingGaps, List<Checkpoint> checkpoints) {}

    /** Per-instance abuse guard, bounded memory; production also needs an edge request limit. */
    public synchronized void rateLimit(String ip) {
        long now = System.currentTimeMillis();
        limits.entrySet().removeIf(entry -> now - entry.getValue()[0] >= 60000);
        String key = Objects.toString(ip, "unknown");
        if (!limits.containsKey(key) && limits.size() >= 10000) throw status(HttpStatus.TOO_MANY_REQUESTS);
        var bucket = limits.computeIfAbsent(key, ignored -> new long[]{now, 0});
        if (++bucket[1] > 120) throw status(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Transactional
    public void save(Dto.ActivityCheckpointRequest request) {
        Long roundId = null;
        if (request.challengeToken() != null) {
            // Lock global session before activity session, matching submission lock order.
            var rounds = jdbc.queryForList("SELECT r.id FROM challenge_platform.challenge_session s JOIN challenge_platform.session_question r ON r.session_id=s.id JOIN challenge_platform.question q ON q.id=r.question_id WHERE s.token_hash=? AND r.ordinal=? AND q.slug=? AND s.finished_at IS NULL AND s.expires_at>clock_timestamp() AND r.attempt_id IS NULL FOR UPDATE OF s", Long.class,
                hashToken(request.challengeToken()), request.ordinal(), request.slug());
            if (rounds.isEmpty()) throw status(HttpStatus.CONFLICT);
            roundId = rounds.get(0);
        }
        String hash = hashToken(request.token());
        String activity = EditorActivityCodec.encode(request.activity(), request.slug());
        if (activity.length() > 20000 || request.activity().events().size() > 100) throw status(HttpStatus.BAD_REQUEST);
        if (request.sequence() == 1) {
            jdbc.update("""
                INSERT INTO challenge_platform.activity_session(token_hash, question_slug)
                SELECT ?, slug FROM challenge_platform.question WHERE slug = ? AND is_active = true
                ON CONFLICT DO NOTHING
                """, hash, request.slug());
        }
        var sessions = jdbc.queryForList("SELECT question_slug, attempt_id, created_at FROM challenge_platform.activity_session WHERE token_hash = ? FOR UPDATE", hash);
        if (sessions.isEmpty()) throw status(HttpStatus.NOT_FOUND);
        var session = sessions.get(0);
        if (roundId != null) {
            var bound = jdbc.queryForObject("SELECT session_question_id FROM challenge_platform.activity_session WHERE token_hash=?", Long.class, hash);
            if (bound != null && !bound.equals(roundId)) throw status(HttpStatus.CONFLICT);
            jdbc.update("UPDATE challenge_platform.activity_session SET session_question_id=? WHERE token_hash=?", roundId, hash);
        }
        if (!request.slug().equals(session.get("question_slug")) || session.get("attempt_id") != null) throw status(HttpStatus.CONFLICT);
        Boolean expired = jdbc.queryForObject("SELECT created_at < now() - interval '1 hour' FROM challenge_platform.activity_session WHERE token_hash = ?", Boolean.class, hash);
        if (Boolean.TRUE.equals(expired)) throw status(HttpStatus.GONE);
        var previous = jdbc.query("SELECT sequence, received_at, source_code, activity_json FROM challenge_platform.activity_checkpoint WHERE token_hash = ? ORDER BY sequence DESC LIMIT 1",
                (rs, n) -> new Checkpoint(rs.getInt(1), rs.getObject(2, OffsetDateTime.class), rs.getString(3), EditorActivityCodec.decode(rs.getString(4))), hash);
        if (!previous.isEmpty()) {
            var last = previous.get(0);
            if (request.sequence() == last.sequence() && request.sourceCode().equals(last.sourceCode()) && request.activity().equals(last.activity())) return;
            if (request.sequence() != last.sequence() + 1 || !request.activity().startedAt().equals(last.activity().startedAt())
                    || request.activity().elapsedMs() < last.activity().elapsedMs()) throw status(HttpStatus.CONFLICT);
            if (last.receivedAt().isAfter(OffsetDateTime.now().minusSeconds(5))) throw status(HttpStatus.TOO_MANY_REQUESTS);
        } else if (request.sequence() != 1) throw status(HttpStatus.CONFLICT);
        jdbc.update("INSERT INTO challenge_platform.activity_checkpoint(token_hash, sequence, source_code, activity_json) VALUES (?, ?, ?, ?)",
                hash, request.sequence(), request.sourceCode(), activity);
    }

    /** Called inside the submission transaction. A token is an unguessable bearer capability, not identity proof. */
    public void attach(String token, String slug, long attemptId) {
        if (token == null) return;
        String hash = hashToken(token);
        // No history (offline/old client) must not block grading; it appears as "not recorded".
        var rows = jdbc.queryForList("SELECT question_slug, attempt_id FROM challenge_platform.activity_session WHERE token_hash = ? FOR UPDATE", hash);
        if (rows.isEmpty()) return;
        var row = rows.get(0);
        if (!slug.equals(row.get("question_slug")) || row.get("attempt_id") != null) throw status(HttpStatus.CONFLICT);
        jdbc.update("UPDATE challenge_platform.activity_session SET attempt_id = ? WHERE token_hash = ?", attemptId, hash);
    }

    public History history(long attemptId, String finalCode) {
        var rows = jdbc.query("""
            SELECT row_number() OVER (ORDER BY c.received_at,c.token_hash,c.sequence) AS sequence, c.received_at, c.source_code, c.activity_json
            FROM challenge_platform.activity_checkpoint c JOIN challenge_platform.activity_session s USING (token_hash)
            WHERE s.attempt_id = ? ORDER BY c.received_at,c.token_hash,c.sequence
            """, (rs, n) -> new Checkpoint(rs.getInt(1), rs.getObject(2, OffsetDateTime.class), rs.getString(3), EditorActivityCodec.decode(rs.getString(4))), attemptId);
        boolean gaps = false;
        for (int i = 1; i < rows.size(); i++) {
            // Only changed states are sent. A long interval can simply mean idle/offline.
            if (rows.get(i).receivedAt().isAfter(rows.get(i - 1).receivedAt().plusSeconds(90))) gaps = true;
        }
        return new History(rows.isEmpty() ? "not-recorded" : "recorded",
                rows.isEmpty() ? null : Objects.equals(finalCode, rows.get(rows.size() - 1).sourceCode()), gaps, rows);
    }

    @Scheduled(initialDelay = 3600000, fixedDelay = 3600000)
    public void expireAbandoned() {
        try { jdbc.update("DELETE FROM challenge_platform.activity_session WHERE attempt_id IS NULL AND created_at < now() - interval '24 hours'"); }
        catch (org.springframework.dao.DataAccessException error) { log.warn("Could not expire abandoned activity sessions."); }
    }

    static String hashToken(String value) {
        try {
            String token = UUID.fromString(value).toString();
            if (!token.equalsIgnoreCase(value)) throw new IllegalArgumentException();
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) { throw status(HttpStatus.BAD_REQUEST); }
    }
    private static ResponseStatusException status(HttpStatus status) { return new ResponseStatusException(status, "Activity checkpoint could not be accepted."); }
}
