package com.qfion.challenge.service;

import com.qfion.challenge.dto.AdminStatsDto.*;
import com.qfion.challenge.repo.CandidateRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class AdminStatsService {
    private final JdbcTemplate jdbc;
    private final ActivityCheckpointService checkpoints;
    private final CandidateRepo candidates;
    private final org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate named;

    // Aggregate before classifying: every submission contributes, including repeated questions.
    private static final String AGGREGATE = """
            WITH aggregate AS (
              SELECT candidate_id, count(*) AS attempt_count, count(DISTINCT question_id) AS questions_attempted,
                sum(testcases_passed) AS testcases_passed, sum(testcases_total) AS testcases_total,
                100.0 * sum(testcases_passed) / NULLIF(sum(testcases_total), 0) AS pass_percentage,
                sum(score) AS total_score, avg(score) AS average_score, sum(duration_ms) AS duration_ms,
                max(submitted_at) AS last_submitted_at
              FROM challenge_platform.attempt
              WHERE submitted_at >= :start AND submitted_at < :end AND submitted_at <= :asOf
              GROUP BY candidate_id
            ), classified AS (
              SELECT aggregate.*, CASE
                WHEN pass_percentage IS NULL THEN 'unavailable'
                WHEN pass_percentage = 0 THEN 'zero'
                WHEN pass_percentage <= 25 THEN 'low'
                WHEN pass_percentage <= 50 THEN 'quarter'
                WHEN pass_percentage <= 75 THEN 'half'
                WHEN pass_percentage < 100 THEN 'high'
                ELSE 'perfect' END AS bucket
              FROM aggregate
            )
            """;
    public record Filters(String search, String campaign, String startDate, String endDate, String timeZone,
                          double minPercent, double maxPercent, String asOf) {
        public static Filters all() { return new Filters("", "", "", "", "UTC", 0, 100, ""); }
    }
    private org.springframework.jdbc.core.namedparam.MapSqlParameterSource parameters(Filters f) {
        if (f.search().length() > 255) throw badRequest("Search must be at most 255 characters.");
        if (f.campaign().length() > 255) throw badRequest("Campaign must be at most 255 characters.");
        if (!Double.isFinite(f.minPercent()) || !Double.isFinite(f.maxPercent())
                || f.minPercent() < 0 || f.maxPercent() > 100 || f.minPercent() > f.maxPercent())
            throw badRequest("Percentage must be between 0 and 100, with minimum no greater than maximum.");
        try {
            var zone = java.time.ZoneId.of(f.timeZone());
            var start = f.startDate().isBlank() ? java.time.LocalDate.of(1900, 1, 1) : java.time.LocalDate.parse(f.startDate());
            var end = f.endDate().isBlank() ? java.time.LocalDate.of(9998, 12, 31) : java.time.LocalDate.parse(f.endDate());
            if (end.isBefore(start)) throw badRequest("End date must not precede start date.");
            var asOf = f.asOf().isBlank() ? java.time.Instant.now() : java.time.Instant.parse(f.asOf());
            if (asOf.isAfter(java.time.Instant.now().plusSeconds(60))) throw badRequest("Snapshot cannot be in the future.");
            return new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                    .addValue("start", java.sql.Timestamp.from(start.atStartOfDay(zone).toInstant()))
                    .addValue("end", java.sql.Timestamp.from(end.plusDays(1).atStartOfDay(zone).toInstant()))
                    .addValue("asOf", java.sql.Timestamp.from(asOf))
                    .addValue("min", f.minPercent()).addValue("max", f.maxPercent())
                    .addValue("search", f.search().trim()).addValue("campaign", f.campaign().trim());
        } catch (java.time.DateTimeException | IllegalArgumentException ex) {
            throw badRequest("Invalid date, time zone, or snapshot.");
        }
    }
    private static final String FILTER = """
             FROM classified a JOIN challenge_platform.candidate c ON c.id = a.candidate_id
             WHERE (a.pass_percentage BETWEEN :min AND :max
               OR (a.pass_percentage IS NULL AND :min = 0 AND :max = 100))
             AND position(lower(:search) in lower(concat_ws(' ', c.full_name, c.email_raw, c.phone_raw))) > 0
             AND (:campaign = '' OR lower(c.source_campaign) = lower(:campaign))
            """;
    private static final Map<String, String> BUCKETS = new LinkedHashMap<>();
    static {
        BUCKETS.put("zero", "0%"); BUCKETS.put("low", ">0–25%");
        BUCKETS.put("quarter", ">25–50%"); BUCKETS.put("half", ">50–75%");
        BUCKETS.put("high", ">75–<100%"); BUCKETS.put("perfect", "100%");
        BUCKETS.put("unavailable", "No test-case data");
    }
    private static final String SUMMARY = """
            a.id, a.question_id, q.slug, q.title, q.language, a.submitted_at, a.duration_ms,
            a.testcases_passed, a.testcases_total,
            round(100.0 * a.testcases_passed / NULLIF(a.testcases_total, 0), 2) AS pass_percentage,
            a.score, a.speed_bonus, a.judge_status
            """;

    public Overview overview() { return overview(Filters.all()); }
    public Overview overview(Filters filters) {
        var params = parameters(filters);
        var rows = named.queryForList(AGGREGATE + "SELECT bucket, count(*) AS count, sum(attempt_count) AS attempts" + FILTER + " GROUP BY bucket", params);
        long total = rows.stream().mapToLong(r -> ((Number) r.get("count")).longValue()).sum();
        long attempts = rows.stream().mapToLong(r -> ((Number) r.get("attempts")).longValue()).sum();
        var counts = new LinkedHashMap<String, Long>();
        rows.forEach(r -> counts.put((String) r.get("bucket"), ((Number) r.get("count")).longValue()));
        List<Bucket> buckets = new ArrayList<>();
        BUCKETS.forEach((key, label) -> {
            long count = counts.getOrDefault(key, 0L);
            buckets.add(new Bucket(key, label, count, total == 0 ? 0 : Math.round(10000.0 * count / total) / 100.0));
        });
        var average = named.queryForObject(AGGREGATE + "SELECT round(coalesce(avg(average_score), 0), 2)" + FILTER, params, java.math.BigDecimal.class);
        return new Overview(total, attempts, "overall", average, buckets);
    }

    public CandidatePage list(String bucket, Filters filters, Long afterId, int size) {
        validatePage(0, size);
        if (afterId != null && afterId <= 0) throw badRequest("Invalid candidate cursor.");
        if (!bucket.equals("all") && !BUCKETS.containsKey(bucket)) throw badRequest("Unknown percentage group.");
        var params = parameters(filters).addValue("bucket", bucket).addValue("cursor", afterId == null ? Long.MAX_VALUE : afterId).addValue("size", size + 1);
        String filter = FILTER + " AND (:bucket = 'all' OR a.bucket = :bucket)";
        long total = named.queryForObject(AGGREGATE + "SELECT count(*)" + filter, params, Long.class);
        var rows = named.query(AGGREGATE + "SELECT c.id, c.full_name, c.email_raw, c.phone_raw, c.source_campaign, a.*" + filter
                + " AND c.id < :cursor ORDER BY c.id DESC LIMIT :size", params,
                (rs, n) -> new CandidateRow(rs.getLong("candidate_id"), rs.getString("full_name"),
                        rs.getString("email_raw"), rs.getString("phone_raw"), rs.getString("source_campaign"), performance(rs)));
        boolean more = rows.size() > size;
        var items = List.copyOf(rows.subList(0, Math.min(size, rows.size())));
        return new CandidatePage(items, total, more ? items.get(items.size() - 1).id() : null);
    }

    private static Performance performance(ResultSet rs) throws SQLException {
        return new Performance(rs.getLong("attempt_count"), rs.getLong("questions_attempted"),
                rs.getLong("testcases_passed"), rs.getLong("testcases_total"),
                rs.getBigDecimal("pass_percentage") == null ? null : rs.getBigDecimal("pass_percentage").setScale(2, java.math.RoundingMode.HALF_UP),
                rs.getBigDecimal("total_score"), rs.getBigDecimal("average_score").setScale(2, java.math.RoundingMode.HALF_UP),
                rs.getObject("duration_ms") == null ? null : ((Number) rs.getObject("duration_ms")).longValue(), rs.getObject("last_submitted_at", OffsetDateTime.class));
    }

    public CandidateDetail candidate(long id, int page, int size) {
        return candidate(id, page, size, "", "UTC", "");
    }
    public CandidateDetail candidate(long id, int page, int size, String day, String timeZone, String asOf) {
        validatePage(page, size);
        var params = parameters(new Filters("", "", day, day, timeZone, 0, 100, asOf))
                .addValue("id", id).addValue("size", size).addValue("offset", (long) page * size);
        var c = candidates.findById(id).orElseThrow(AdminStatsService::notFound);
        String scoped = " WHERE a.candidate_id = :id AND a.submitted_at >= :start AND a.submitted_at < :end AND a.submitted_at <= :asOf ";
        long total = named.queryForObject("SELECT count(*) FROM challenge_platform.attempt a" + scoped, params, Long.class);
        var attempts = named.query("SELECT " + SUMMARY + " FROM challenge_platform.attempt a "
                + "JOIN challenge_platform.question q ON q.id = a.question_id" + scoped
                + "ORDER BY a.submitted_at DESC, a.id DESC LIMIT :size OFFSET :offset", params, (rs, n) -> summary(rs));
        var performances = named.query(AGGREGATE + "SELECT * FROM classified WHERE candidate_id = :id", params, (rs, n) -> performance(rs));
        var performance = performances.isEmpty() ? new Performance(0, 0, 0, 0, null,
                java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, null, null) : performances.get(0);
        return new CandidateDetail(id, c.getFullName(), c.getEmailRaw(), c.getPhoneRaw(), Boolean.TRUE.equals(c.getIsConsented()),
                c.getSourceCampaign(), c.getFirstSeenAt(), c.getLastSeenAt(), performance, new Page<>(attempts, total, page, size));
    }

    public AttemptDetail attempt(long candidateId, long attemptId) {
        var rows = jdbc.query("SELECT " + SUMMARY + ", a.source_code, q.prompt, q.difficulty, q.time_limit_seconds, "
                + "q.starter_code, q.reference_solution, a.ip_address, a.user_agent, a.editor_activity_json "
                + "FROM challenge_platform.attempt a JOIN challenge_platform.question q ON q.id = a.question_id "
                + "WHERE a.id = ? AND a.candidate_id = ?", (rs, n) -> new AttemptDetail(summary(rs),
                        rs.getString("source_code"), rs.getString("prompt"), rs.getInt("difficulty"), rs.getInt("time_limit_seconds"),
                        rs.getString("starter_code"), rs.getString("reference_solution"), rs.getString("ip_address"),
                        rs.getString("user_agent"), List.of(), EditorActivityCodec.decode(rs.getString("editor_activity_json")), null), attemptId, candidateId);
        if (rows.isEmpty()) throw notFound();
        var a = rows.get(0);
        var cases = jdbc.query("""
                SELECT t.id, t.ordinal, t.is_sample, t.stdin, t.expected_output,
                  r.is_passed, r.judge_status, r.exec_time_ms, r.memory_kb, r.stdout_text
                FROM challenge_platform.attempt_result r
                JOIN challenge_platform.question_testcase t ON t.id = r.testcase_id
                WHERE r.attempt_id = ? ORDER BY t.ordinal, t.id
                """, (rs, n) -> new CaseResult(rs.getLong("id"), rs.getInt("ordinal"), rs.getBoolean("is_sample"),
                        rs.getString("stdin"), rs.getString("expected_output"), (Boolean) rs.getObject("is_passed"),
                        rs.getString("judge_status"), (Integer) rs.getObject("exec_time_ms"), (Integer) rs.getObject("memory_kb"),
                        rs.getString("stdout_text")), attemptId);
        return new AttemptDetail(a.summary(), a.sourceCode(), a.prompt(), a.difficulty(), a.timeLimitSeconds(),
                a.starterCode(), a.referenceSolution(), a.ipAddress(), a.userAgent(), cases, a.editorActivity(), checkpoints.history(attemptId, a.sourceCode()));
    }

    private static AttemptSummary summary(ResultSet rs) throws SQLException {
        return new AttemptSummary(rs.getLong("id"), rs.getLong("question_id"), rs.getString("slug"), rs.getString("title"),
                rs.getString("language"), rs.getObject("submitted_at", OffsetDateTime.class), (Long) rs.getObject("duration_ms"),
                rs.getInt("testcases_passed"), rs.getInt("testcases_total"), rs.getBigDecimal("pass_percentage"),
                rs.getBigDecimal("score"), rs.getBigDecimal("speed_bonus"), rs.getString("judge_status"));
    }
    private static void validatePage(int page, int size) {
        if (page < 0 || page > 1000000 || size < 1 || size > 100) throw badRequest("Page must be non-negative and size must be 1–100.");
    }
    private static ResponseStatusException badRequest(String reason) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason); }
    private static ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidate or attempt not found."); }
}
