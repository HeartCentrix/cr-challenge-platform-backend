package com.qfion.challenge.service;

import com.qfion.challenge.dto.*;
import com.qfion.challenge.entity.*;
import com.qfion.challenge.repo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class ChallengeSessionService {
    private final JdbcTemplate jdbc;
    private final CandidateRepo candidates;
    private final QuestionRepo questions;
    private final TestcaseRepo testcases;
    private final AttemptRepo attempts;
    private final IdentityService identities;
    private final CandidateRegionService regions;

    record Session(long id, long candidateId, OffsetDateTime started, OffsetDateTime expires, OffsetDateTime finished, String reason) {}
    record Round(long id, long questionId, int ordinal, OffsetDateTime issued, String draft, String activity,
                 OffsetDateTime draftAt, int revision, Long attemptId) {}

    @Transactional
    public SessionDto.State start(SessionDto.Start req, String ip, String userAgent) {
        String hash = ActivityCheckpointService.hashToken(req.token());
        gate();
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", (org.springframework.jdbc.core.RowCallbackHandler) r -> {}, "challenge-token:" + hash);
        if (jdbc.queryForObject("SELECT count(*) FROM challenge_platform.challenge_session WHERE token_hash = ?", Long.class, hash) > 0) return state(req.token());
        String email = identities.normaliseEmail(req.email()), phone = identities.normalisePhone(req.phone());
        if (phone.length() < 7) throw error(HttpStatus.BAD_REQUEST, "Enter a valid phone number.");
        String eh = identities.hash(email), ph = identities.hash(phone);
        Stream.of("challenge-submit:EMAIL:" + eh, "challenge-submit:PHONE:" + ph).sorted().forEach(key ->
            jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", (org.springframework.jdbc.core.RowCallbackHandler) r -> {}, key));
        LocalDate today = LocalDate.now();
        if (jdbc.queryForObject("SELECT count(*) FROM challenge_platform.daily_attempt_lock WHERE attempt_date = ? AND ((identity_type = 'EMAIL' AND identity_hash = ?) OR (identity_type = 'PHONE' AND identity_hash = ?))", Long.class, today, eh, ph) > 0)
            throw error(HttpStatus.TOO_MANY_REQUESTS, "You have already started today's challenge.");
        Candidate candidate = candidates.findByEmailNormalised(email).or(() -> candidates.findByPhoneNormalised(phone)).orElse(null);
        OffsetDateTime now = now();
        if (candidate == null) candidate = Candidate.builder().fullName(req.fullName()).emailRaw(req.email()).emailNormalised(email)
            .phoneRaw(req.phone()).phoneNormalised(phone).isConsented(false).sourceCampaign(req.sourceCampaign())
            .firstSeenAt(now).lastSeenAt(now).build();
        else { candidate.setFullName(req.fullName()); candidate.setLastSeenAt(now); }
        candidate = candidates.saveAndFlush(candidate);
        long id = jdbc.queryForObject("""
            INSERT INTO challenge_platform.challenge_session(token_hash,candidate_id,session_date,started_at,expires_at,ip_address,user_agent)
            VALUES (?,?,?,?,?,?,?) RETURNING id
            """, Long.class, hash, candidate.getId(), today, now, now.plusMinutes(10), ip, userAgent);
        var session = new Session(id, candidate.getId(), now, now.plusMinutes(10), null, null);
        if (!assign(session, now)) throw error(HttpStatus.SERVICE_UNAVAILABLE, "No challenge questions are currently available.");
        for (var identity : List.of(new String[]{"EMAIL", eh}, new String[]{"PHONE", ph})) {
            jdbc.update("INSERT INTO challenge_platform.daily_attempt_lock(identity_type,identity_hash,attempt_date,candidate_id,challenge_session_id,created_at) VALUES (?,?,?,?,?,?)",
                identity[0], identity[1], today, candidate.getId(), id, now);
        }
        return view(session, now);
    }

    @Transactional
    public SessionDto.State state(String token) {
        gate();
        var session = lock(token);
        OffsetDateTime now = now();
        if (session.finished() == null && !now.isBefore(session.expires())) session = finishExpired(session);
        return view(session, now);
    }

    @Transactional
    public SessionDto.State answer(SessionDto.Answer req, String ip, String userAgent) {
        gate();
        var session = lock(req.token());
        OffsetDateTime now = now();
        var round = round(session.id(), req.ordinal());
        if (session.finished() != null) return view(session, now);
        if (!now.isBefore(session.expires())) return view(finishExpired(session), now);
        if (round.attemptId() != null) return view(session, now); // Lost acknowledgement / double click: never regrade.
        if (latest(session.id()).ordinal() != round.ordinal()) throw error(HttpStatus.CONFLICT, "This question is no longer active.");
        queue(session, round, req.sourceCode(), EditorActivityCodec.encode(req.editorActivity(), question(round).getSlug()), now, ip, userAgent);
        if (!assign(session, now)) session = finish(session, now, "ALL_QUESTIONS_SUBMITTED");
        return view(session, now());
    }

    @Transactional
    public void draft(SessionDto.Draft req) {
        var session = lock(req.token());
        OffsetDateTime now = now();
        if (session.finished() != null || !now.isBefore(session.expires())) return;
        var round = round(session.id(), req.ordinal());
        if (round.attemptId() != null || latest(session.id()).ordinal() != round.ordinal()) return;
        String activity = EditorActivityCodec.encode(req.editorActivity(), question(round).getSlug());
        // Monotonic revisions prevent an older, delayed autosave overwriting a newer draft.
        jdbc.update("UPDATE challenge_platform.session_question SET draft_code=?,draft_activity=?,draft_received_at=?,draft_revision=? WHERE id=? AND draft_revision < ?",
            req.sourceCode(), activity, now, req.revision(), round.id(), req.revision());
    }

    @Transactional
    public SessionDto.State finish(String token) {
        gate();
        var session = lock(token);
        OffsetDateTime now = now();
        if (session.finished() != null) return view(session, now);
        if (!now.isBefore(session.expires())) return view(finishExpired(session), now);
        saveLastDraft(session);
        return view(finish(session, now, "FINISHED"), now);
    }

    @Transactional
    public boolean expireOne() {
        // Same gate order as answer/start/reset. Safe across multiple backend instances.
        gate();
        var ids = jdbc.queryForList("SELECT id FROM challenge_platform.challenge_session WHERE finished_at IS NULL AND expires_at <= clock_timestamp() ORDER BY expires_at FOR UPDATE SKIP LOCKED LIMIT 1", Long.class);
        if (!ids.isEmpty()) finishExpired(load(ids.get(0)));
        return !ids.isEmpty();
    }

    private Session finishExpired(Session session) { saveLastDraft(session); return finish(session, session.expires(), "TIME_UP"); }
    private void saveLastDraft(Session session) {
        var round = latest(session.id());
        if (round.attemptId() == null && round.draftAt() != null && round.draftAt().isBefore(session.expires())
                && round.draft() != null && !round.draft().isBlank()) {
            var metadata = jdbc.queryForMap("SELECT ip_address,user_agent FROM challenge_platform.challenge_session WHERE id=?", session.id());
            queue(session, round, round.draft(), round.activity(), round.draftAt(), (String) metadata.get("ip_address"), (String) metadata.get("user_agent"));
        }
    }
    private void queue(Session session, Round round, String source, String activity, OffsetDateTime receivedAt, String ip, String userAgent) {
        var q = question(round);
        var cases = testcases.findByQuestionIdOrderByOrdinalAsc(q.getId());
        if (cases.isEmpty()) throw error(HttpStatus.SERVICE_UNAVAILABLE, "This question is unavailable.");
        var attempt = attempts.saveAndFlush(Attempt.builder().candidateId(session.candidateId()).questionId(q.getId())
            .submittedAt(receivedAt).durationMs(Math.max(0, Duration.between(round.issued(), receivedAt).toMillis()))
            .judgeLanguageId(q.getJudgeLanguageId()).sourceCode(source).editorActivityJson(activity)
            .testcasesPassed(0).testcasesTotal(cases.size()).score(BigDecimal.ZERO).speedBonus(BigDecimal.ZERO)
            .judgeStatus("QUEUED").ipAddress(ip).userAgent(userAgent).build());
        jdbc.update("UPDATE challenge_platform.attempt SET challenge_session_id=?,session_elapsed_ms=?,region_code=? WHERE id=?",
            session.id(), Math.max(0, Duration.between(session.started(), receivedAt).toMillis()), regions.resolve(ip), attempt.getId());
        jdbc.update("UPDATE challenge_platform.session_question SET attempt_id=?,draft_code=NULL,draft_activity=NULL WHERE id=?", attempt.getId(), round.id());
        jdbc.update("UPDATE challenge_platform.activity_session SET attempt_id=? WHERE session_question_id=? AND attempt_id IS NULL", attempt.getId(), round.id());
    }
    private Session finish(Session session, OffsetDateTime at, String reason) {
        jdbc.update("UPDATE challenge_platform.challenge_session SET finished_at=?,finish_reason=? WHERE id=?", at, reason, session.id());
        return new Session(session.id(), session.candidateId(), session.started(), session.expires(), at, reason);
    }
    private boolean assign(Session session, OffsetDateTime at) {
        var ids = jdbc.queryForList("SELECT q.id FROM challenge_platform.question q WHERE q.is_active AND NOT EXISTS (SELECT 1 FROM challenge_platform.session_question r WHERE r.session_id=? AND r.question_id=q.id) AND EXISTS (SELECT 1 FROM challenge_platform.question_testcase t WHERE t.question_id=q.id) ORDER BY random() LIMIT 1", Long.class, session.id());
        if (ids.isEmpty()) return false;
        jdbc.update("INSERT INTO challenge_platform.session_question(session_id,question_id,ordinal,issued_at) SELECT ?,?,coalesce(max(ordinal),0)+1,? FROM challenge_platform.session_question WHERE session_id=?", session.id(), ids.get(0), at, session.id());
        return true;
    }
    private SessionDto.State view(Session s, OffsetDateTime now) {
        var r = latest(s.id());
        boolean active = s.finished() == null && now.isBefore(s.expires());
        Dto.QuestionDetail detail = null;
        if (active) {
            var q = question(r);
            var samples = testcases.findByQuestionIdAndIsSampleTrueOrderByOrdinalAsc(q.getId()).stream().map(t -> new Dto.SampleTestcase(t.getStdin(), t.getExpectedOutput())).toList();
            detail = new Dto.QuestionDetail(q.getId(), q.getSlug(), q.getTitle(), q.getPrompt(), q.getDifficulty(), q.getLanguage(), q.getJudgeLanguageId(), q.getStarterCode(), 600, samples);
        }
        int submitted = jdbc.queryForObject("SELECT count(*) FROM challenge_platform.session_question WHERE session_id=? AND attempt_id IS NOT NULL", Integer.class, s.id());
        return new SessionDto.State(active ? "ACTIVE" : "FINISHED", s.reason(), now, s.started(), s.expires(), s.finished(), submitted, r.ordinal(), detail, active ? r.draft() : null, r.revision());
    }
    private Session lock(String token) {
        var ids = jdbc.queryForList("SELECT id FROM challenge_platform.challenge_session WHERE token_hash=? FOR UPDATE", Long.class, ActivityCheckpointService.hashToken(token));
        if (ids.isEmpty()) throw error(HttpStatus.NOT_FOUND, "Challenge session not found.");
        return load(ids.get(0));
    }
    private Session load(long id) {
        return jdbc.queryForObject("SELECT * FROM challenge_platform.challenge_session WHERE id=?", (rs,n) -> new Session(rs.getLong("id"),rs.getLong("candidate_id"),rs.getObject("started_at",OffsetDateTime.class),rs.getObject("expires_at",OffsetDateTime.class),rs.getObject("finished_at",OffsetDateTime.class),rs.getString("finish_reason")), id);
    }
    private Round latest(long id) { return rounds("SELECT * FROM challenge_platform.session_question WHERE session_id=? ORDER BY ordinal DESC LIMIT 1", id).get(0); }
    private Round round(long id, int ordinal) {
        var rows = rounds("SELECT * FROM challenge_platform.session_question WHERE session_id=? AND ordinal=?", id, ordinal);
        if (rows.isEmpty()) throw error(HttpStatus.NOT_FOUND, "Question not found in this session.");
        return rows.get(0);
    }
    private List<Round> rounds(String sql, Object... args) {
        return jdbc.query(sql,(rs,n)->new Round(rs.getLong("id"),rs.getLong("question_id"),rs.getInt("ordinal"),rs.getObject("issued_at",OffsetDateTime.class),rs.getString("draft_code"),rs.getString("draft_activity"),rs.getObject("draft_received_at",OffsetDateTime.class),rs.getInt("draft_revision"),(Long)rs.getObject("attempt_id")),args);
    }
    private Question question(Round r) { return questions.findById(r.questionId()).orElseThrow(); }
    private OffsetDateTime now() { return jdbc.queryForObject("SELECT clock_timestamp()", OffsetDateTime.class); }
    private void gate() { jdbc.query("SELECT pg_advisory_xact_lock_shared(hashtextextended('challenge-daily-reset',0))",(org.springframework.jdbc.core.RowCallbackHandler)r->{}); }
    private static ResponseStatusException error(HttpStatus status, String message) { return new ResponseStatusException(status,message); }
}
