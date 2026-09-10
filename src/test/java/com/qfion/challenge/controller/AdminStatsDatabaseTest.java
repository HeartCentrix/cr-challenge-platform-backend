package com.qfion.challenge.controller;

import com.qfion.challenge.service.AdminStatsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in local PostgreSQL integration test. All fixture rows roll back. */
@SpringBootTest(properties={"challenge.sessions.workers-enabled=false","challenge.regions.backfill-enabled=false"})
@EnabledIfEnvironmentVariable(named = "CHALLENGE_STATS_DB_TESTS", matches = "true")
@Transactional(isolation = Isolation.REPEATABLE_READ)
class AdminStatsDatabaseTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired AdminStatsService stats;

    @Test void countsOverallCandidatesAndSupportsFiltersCursorPaginationAndDetails() {
        String server = jdbc.queryForObject("SELECT host(inet_server_addr())", String.class);
        assertTrue("127.0.0.1".equals(server) || "::1".equals(server), "Fixtures are local-only");
        assertEquals("challenge_platform", jdbc.queryForObject("SELECT current_database()", String.class));
        var before = stats.overview();
        long question = jdbc.queryForObject("SELECT min(id) FROM challenge_platform.question", Long.class);
        String prefix = "stats-test-" + UUID.randomUUID();
        int[][] cases = {{0,10},{1,4},{1,2},{3,4},{99,100},{1,1},{0,0},{1,3}};
        String[] groups = {"quarter","low","quarter","half","high","perfect","unavailable","quarter"};
        long firstCandidate = 0, latestAttempt = 0;
        for (int i = 0; i < cases.length; i++) {
            String email = prefix + i + "@example.invalid";
            long candidate = jdbc.queryForObject("INSERT INTO challenge_platform.candidate(full_name,email_raw,email_normalised,phone_raw,phone_normalised) "
                    + "VALUES (?, ?, ?, '2025550196', '2025550196') RETURNING id", Long.class, prefix + i, email, email);
            if (i == 0) {
                firstCandidate = candidate;
                insertAttempt(candidate, question, 10, 10, "2000-01-01");
            }
            long attempt = insertAttempt(candidate, question, cases[i][0], cases[i][1], "2001-01-01");
            if (i == 0) latestAttempt = attempt;
            var match = stats.list(groups[i], filters(email), null, 25);
            assertEquals(1, match.total());
            assertEquals(candidate, match.items().get(0).id());
        }
        var after = stats.overview();
        assertEquals(before.totalCandidates() + 8, after.totalCandidates());
        assertEquals(before.totalAttempts() + 9, after.totalAttempts());
        assertEquals(after.totalCandidates(), after.buckets().stream().mapToLong(b -> b.count()).sum());
        assertEquals(8, stats.list("all", filters(prefix.toUpperCase()), null, 3).total());
        var first = stats.list("all", filters(prefix), null, 3);
        var second = stats.list("all", filters(prefix), first.nextCursor(), 3);
        var third = stats.list("all", filters(prefix), second.nextCursor(), 3);
        assertEquals(3, first.items().size());
        assertEquals(3, second.items().size());
        assertEquals(2, third.items().size());
        assertNull(third.nextCursor());
        assertEquals(8, java.util.stream.Stream.of(first, second, third).flatMap(p -> p.items().stream()).map(c -> c.id()).distinct().count());
        assertEquals(0, stats.list("all", filters("' OR 1=1 --"), null, 25).total());
        var detail = stats.candidate(firstCandidate, 0, 10);
        assertEquals(2, detail.attempts().total());
        assertEquals(50, detail.performance().passPercentage().doubleValue());
        assertEquals(50, detail.performance().averageScore().doubleValue());
        assertEquals(100, detail.performance().totalScore().doubleValue());
        assertEquals(20, detail.performance().testcasesTotal());
        assertEquals(1, detail.performance().questionsAttempted());
        var dateFilter = new AdminStatsService.Filters(prefix, "", "2000-01-01", "2000-01-01", "UTC", 0, 100, "");
        var dated = stats.overview(dateFilter);
        assertEquals(1, dated.totalCandidates());
        assertEquals(1, dated.totalAttempts());
        assertEquals(100, dated.averageCandidateScore().doubleValue());
        assertEquals("overall", dated.basis());
        assertEquals(1, stats.list("perfect", dateFilter, null, 25).total());
        assertEquals(0, stats.overview(new AdminStatsService.Filters(prefix, "", "2000-01-01", "2000-01-01", "Asia/Kolkata", 0, 99, "")).totalCandidates());
        assertEquals(3, stats.overview(new AdminStatsService.Filters(prefix, "", "", "", "UTC", 25.01, 50, "")).totalCandidates());
        jdbc.update("UPDATE challenge_platform.candidate SET source_campaign = 'linkedin' WHERE id = ?", firstCandidate);
        var campaign = new AdminStatsService.Filters(prefix, "LINKEDIN", "", "", "UTC", 0, 100, "");
        assertEquals(1, stats.overview(campaign).totalCandidates());
        assertEquals("linkedin", stats.list("all", campaign, null, 25).items().get(0).sourceCampaign());
        assertThrows(ResponseStatusException.class, () -> stats.overview(new AdminStatsService.Filters("", "", "2001-01-02", "2001-01-01", "UTC", 0, 100, "")));
        assertThrows(ResponseStatusException.class, () -> stats.overview(new AdminStatsService.Filters("", "", "", "", "invalid", 0, 100, "")));
        assertThrows(ResponseStatusException.class, () -> stats.overview(new AdminStatsService.Filters("", "", "", "", "UTC", 80, 20, "")));
        assertThrows(ResponseStatusException.class, () -> stats.overview(new AdminStatsService.Filters("", "", "", "", "UTC", Double.NaN, 100, "")));
        assertEquals(latestAttempt, detail.attempts().items().get(0).id());
        jdbc.update("UPDATE challenge_platform.attempt SET region_code='TX' WHERE candidate_id=?",firstCandidate);
        jdbc.update("UPDATE challenge_platform.attempt SET region_code='CA' WHERE id=?",latestAttempt);
        var california = new AdminStatsService.Filters(prefix,"","","","UTC",0,100,"","CA");
        var regional = stats.list("all",california,null,1);
        assertEquals(1,regional.total()); assertNull(regional.nextCursor());
        assertEquals("California",regional.items().get(0).region());
        assertEquals(2,regional.items().get(0).performance().attemptCount()); // Region selects candidates, not individual answers.
        assertEquals(1,stats.overview(california).totalCandidates());
        assertEquals(0,stats.overview(new AdminStatsService.Filters(prefix,"","","","UTC",0,100,"","TX")).totalCandidates());
        assertEquals(1,stats.overview(new AdminStatsService.Filters(prefix,"","2000-01-01","2000-01-01","UTC",0,100,"","TX")).totalCandidates());
        assertEquals(1,stats.overview(new AdminStatsService.Filters(prefix,"","","","UTC",0,100,"2000-12-31T00:00:00Z","TX")).totalCandidates());
        assertEquals(7,stats.overview(new AdminStatsService.Filters(prefix,"","","","UTC",0,100,"","UNKNOWN")).totalCandidates());
        assertThrows(ResponseStatusException.class,()->stats.overview(new AdminStatsService.Filters("","","","","UTC",0,100,"","' OR 1=1")));
        long testcase = jdbc.queryForObject("SELECT min(id) FROM challenge_platform.question_testcase WHERE question_id = ?", Long.class, question);
        jdbc.update("INSERT INTO challenge_platform.attempt_result(attempt_id,testcase_id,is_passed,stdout_text) VALUES (?, ?, false, 'saved output')", latestAttempt, testcase);
        var attempt = stats.attempt(firstCandidate, latestAttempt);
        assertEquals("fixture code", attempt.sourceCode());
        assertEquals("saved output", attempt.testcases().get(0).stdout());
        assertFalse(attempt.aiMarkerDetected());
        assertFalse(stats.list("all", filters(prefix), null, 25).items().stream().anyMatch(c -> c.aiMarkerDetected()));
        String marker = "/*" + String.valueOf((char) 173).repeat(64) + "*/";
        jdbc.update("UPDATE challenge_platform.attempt SET source_code = ? WHERE id = ?", "class Main { " + marker + " }", latestAttempt);
        assertTrue(stats.attempt(firstCandidate, latestAttempt).aiMarkerDetected());
        var flagged = stats.list("all", filters(prefix), null, 25).items().stream().filter(c -> c.aiMarkerDetected()).toList();
        assertEquals(1, flagged.size());
        assertEquals(firstCandidate, flagged.get(0).id());
        assertFalse(stats.list("all", dateFilter, null, 25).items().get(0).aiMarkerDetected());
        assertFalse(stats.list("all", new AdminStatsService.Filters(prefix,"","","","UTC",0,100,"2000-12-31T00:00:00Z"), null, 25).items().get(0).aiMarkerDetected());
        assertTrue(stats.list("all", california, null, 25).items().get(0).aiMarkerDetected());
        long wrongCandidate = firstCandidate + 1;
        assertThrows(ResponseStatusException.class, () -> stats.attempt(wrongCandidate, attempt.summary().id()));
        assertThrows(ResponseStatusException.class, () -> stats.list("invalid", filters(""), null, 25));
        assertThrows(ResponseStatusException.class, () -> stats.list("all", filters(""), -1L, 25));
        assertThrows(ResponseStatusException.class, () -> stats.list("all", filters(""), null, 101));
    }

    private AdminStatsService.Filters filters(String search) {
        return new AdminStatsService.Filters(search, "", "", "", "UTC", 0, 100, "");
    }

    @Test void candidateDayUsesLocalMidnightBoundsForPagingAndTotalsIncludingDst() {
        String server = jdbc.queryForObject("SELECT host(inet_server_addr())", String.class);
        assertTrue("127.0.0.1".equals(server) || "::1".equals(server), "Fixtures are local-only");
        assertEquals("challenge_platform", jdbc.queryForObject("SELECT current_database()", String.class));
        long question = jdbc.queryForObject("SELECT min(id) FROM challenge_platform.question", Long.class);
        String email = "day-test-" + UUID.randomUUID() + "@example.invalid";
        long candidate = jdbc.queryForObject("INSERT INTO challenge_platform.candidate(full_name,email_raw,email_normalised,phone_raw,phone_normalised) "
                + "VALUES ('Day fixture', ?, ?, '2025550196', '2025550196') RETURNING id", Long.class, email, email);
        for (String[] scenario : new String[][] { {"2025-01-02", "Asia/Kolkata"}, {"2025-03-09", "America/New_York"}, {"2025-11-02", "America/New_York"} }) {
            var day = java.time.LocalDate.parse(scenario[0]); var zone = java.time.ZoneId.of(scenario[1]);
            var start = day.atStartOfDay(zone).toInstant(); var end = day.plusDays(1).atStartOfDay(zone).toInstant();
            for (var instant : List.of(start.minusSeconds(1), start, end.minusSeconds(1), end)) {
                long id = insertAttempt(candidate, question, 1, 2, "2000-01-01");
                jdbc.update("UPDATE challenge_platform.attempt SET submitted_at = ? WHERE id = ?", java.sql.Timestamp.from(instant), id);
            }
            var first = stats.candidate(candidate, 0, 1, scenario[0], scenario[1], "");
            var second = stats.candidate(candidate, 1, 1, scenario[0], scenario[1], "");
            assertEquals(2, first.attempts().total()); assertEquals(1, first.attempts().items().size());
            assertNotEquals(first.attempts().items().get(0).id(), second.attempts().items().get(0).id());
            assertEquals(2, first.performance().attemptCount()); assertEquals(4, first.performance().testcasesTotal());
            assertEquals(100, first.performance().totalScore().doubleValue());
            assertEquals(start, second.attempts().items().get(0).submittedAt().toInstant());
            assertEquals(end.minusSeconds(1), first.attempts().items().get(0).submittedAt().toInstant());
        }
        var empty = stats.candidate(candidate, 0, 10, "1999-01-01", "Asia/Kolkata", "");
        assertEquals(0, empty.attempts().total()); assertTrue(empty.attempts().items().isEmpty());
        assertEquals(0, empty.performance().attemptCount()); assertNull(empty.performance().passPercentage());
        assertThrows(ResponseStatusException.class, () -> stats.candidate(candidate, 0, 10, "invalid", "UTC", ""));
    }

    private long insertAttempt(long candidate, long question, int passed, int total, String date) {
        return jdbc.queryForObject("INSERT INTO challenge_platform.attempt(candidate_id,question_id,judge_language_id,source_code,testcases_passed,testcases_total,submitted_at,score,duration_ms) "
                + "VALUES (?, ?, 62, 'fixture code', ?, ?, ?::timestamptz, ?, 1000) RETURNING id", Long.class, candidate, question, passed, total, date + "T12:00:00Z", total == 0 ? 0 : 100.0 * passed / total);
    }
}
