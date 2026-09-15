package com.qfion.challenge.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qfion.challenge.dto.*;
import com.qfion.challenge.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties={"challenge.sessions.workers-enabled=false","challenge.regions.backfill-enabled=false"})
@EnabledIfEnvironmentVariable(named="CHALLENGE_STATS_DB_TESTS",matches="true")
@Transactional
class ChallengeSessionDatabaseTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired ChallengeSessionService sessions;
    @Autowired SubmissionService submissions;
    @Autowired FollowupService followups;
    @Autowired ActivityCheckpointService checkpoints;
    @Autowired AdminStatsService stats;
    @Autowired DailyLimitResetService resets;
    @MockBean Judge0Client judge;
    final String token = UUID.randomUUID().toString();
    SessionDto.Start request;

    @BeforeEach void localOnly() {
        assertEquals("challenge_platform",jdbc.queryForObject("SELECT current_database()",String.class));
        assertTrue(List.of("127.0.0.1","::1").contains(jdbc.queryForObject("SELECT host(inet_server_addr())",String.class)));
        request = new SessionDto.Start(token,"Session Fixture",UUID.randomUUID()+"@example.invalid",
            "+1202"+String.format("%07d",Math.floorMod(UUID.randomUUID().hashCode(),10000000)),"session-test");
    }
    long id() { return jdbc.queryForObject("SELECT s.id FROM challenge_platform.challenge_session s JOIN challenge_platform.candidate c ON c.id=s.candidate_id WHERE c.email_normalised=?",Long.class,request.email()); }
    SessionDto.State completeFollowups(SessionDto.State current) {
        while(current.phase().equals("FOLLOW_UP")) {
            var f=current.followup();
            var answers=f.kind().equals("DRAG_DROP")?f.options().stream().map(FollowupService.Option::id).toList():
                f.kind().equals("MCQ")?List.of(f.options().get(0).id()):List.of("fixture answer");
            var req=new SessionDto.FollowupAnswer(token,current.ordinal(),f.ordinal(),answers,
                f.debug()==null?null:f.debug().prefix()+answers.get(0)+f.debug().suffix());
            var next=sessions.followup(req);
            assertEquals(current.expiresAt(),next.expiresAt());
            var retry=sessions.followup(req);
            assertEquals(next.ordinal(),retry.ordinal());
            assertEquals(next.followup(),retry.followup());
            current=next;
        }
        return current;
    }

    @Test void oneDeadlineNonRepeatingQuestionsIdempotencyAndAsynchronousGrading() throws Exception {
        var first=sessions.start(request,"127.0.0.1","test");
        assertEquals(Duration.ofMinutes(10),Duration.between(first.startedAt(),first.expiresAt()));
        assertEquals(first.expiresAt(),sessions.start(request,"127.0.0.1","test").expiresAt());
        assertEquals(first.question(),sessions.state(token).question());
        var activity=new ObjectMapper().readValue(EditorActivityTest.ACTIVITY.replace("test-question",first.question().slug()),Dto.EditorActivity.class);
        // Independent tracking streams after reload both stay attached to this question.
        for(int i=0;i<2;i++) checkpoints.save(new Dto.ActivityCheckpointRequest(UUID.randomUUID().toString(),1,first.question().slug(),"answer one",activity,token,1));
        var answer=new SessionDto.Answer(token,1,"answer one",activity);
        var second=sessions.answer(answer,"127.0.0.1","test");
        assertEquals("FOLLOW_UP",second.phase());
        assertEquals(first.question().id(),second.question().id());
        second=completeFollowups(second);
        assertEquals(first.expiresAt(),second.expiresAt());
        assertNotEquals(first.question().id(),second.question().id());
        assertEquals(2,second.ordinal()); assertEquals(1,second.submittedAnswers());
        assertEquals(1,sessions.answer(answer,"127.0.0.1","test").submittedAnswers());
        verifyNoInteractions(judge);
        var row=jdbc.queryForMap("SELECT id,candidate_id,judge_status FROM challenge_platform.attempt WHERE challenge_session_id=?",id());
        assertEquals("QUEUED",row.get("judge_status"));
        var detail=stats.attempt((Long)row.get("candidate_id"),(Long)row.get("id"));
        assertEquals(2,detail.checkpointHistory().checkpoints().size());
        assertEquals(activity,detail.editorActivity());
        assertEquals(id(),detail.sessionTiming().sessionId());
        assertEquals(1,detail.sessionTiming().questionNumber());
        assertEquals(first.expiresAt(),detail.sessionTiming().expiresAt());
        when(judge.execute(anyString(),anyInt(),nullable(String.class),nullable(String.class)))
            .thenReturn(new Judge0Client.Execution(3,"Accepted","",null,1,1));
        submissions.gradeNextQueued();
        assertEquals("Accepted",jdbc.queryForObject("SELECT judge_status FROM challenge_platform.attempt WHERE id=?",String.class,row.get("id")));
        assertFalse(new ObjectMapper().findAndRegisterModules().writeValueAsString(second).contains("score"));
        assertEquals(429,assertThrows(ResponseStatusException.class,()->sessions.start(new SessionDto.Start(UUID.randomUUID().toString(),request.fullName(),request.email(),request.phone(),"test"),"127.0.0.1","test")).getStatusCode().value());
    }

    @Test void expiryUsesOnlyLatestServerSavedDraftAndRejectsLatePayload() {
        sessions.start(request,"127.0.0.1","test");
        sessions.draft(new SessionDto.Draft(token,1,2,"new draft",null));
        sessions.draft(new SessionDto.Draft(token,1,1,"stale draft",null));
        assertEquals("new draft",sessions.state(token).draftCode());
        // Move the whole fixture timeline consistently; never wait ten real minutes.
        jdbc.update("UPDATE challenge_platform.challenge_session SET started_at=started_at-interval '11 minutes',expires_at=expires_at-interval '11 minutes' WHERE id=?",id());
        jdbc.update("UPDATE challenge_platform.session_question SET issued_at=issued_at-interval '11 minutes',draft_received_at=draft_received_at-interval '11 minutes' WHERE session_id=?",id());
        var end=sessions.answer(new SessionDto.Answer(token,1,"late replacement",null),"127.0.0.1","test");
        assertEquals("FINISHED",end.status()); assertEquals("TIME_UP",end.reason()); assertNull(end.question());
        assertEquals(1,end.submittedAnswers());
        assertEquals("new draft",jdbc.queryForObject("SELECT source_code FROM challenge_platform.attempt WHERE challenge_session_id=?",String.class,id()));
        assertEquals(1,sessions.finish(token).submittedAnswers());
        verifyNoInteractions(judge);
    }

    @Test void followupsCannotBeAnsweredOutOfOrderAndUsePrivateStableSnapshots() throws Exception {
        var first=sessions.start(request,"127.0.0.1","test");
        assertEquals("CODING",first.phase()); assertNull(first.followup());
        assertThrows(ResponseStatusException.class,()->sessions.followup(new SessionDto.FollowupAnswer(token,1,1,List.of("x"))));
        var current=sessions.answer(new SessionDto.Answer(token,1,"code",null),"127.0.0.1","test");
        assertEquals(1,current.followup().ordinal());
        assertThrows(ResponseStatusException.class,()->sessions.followup(new SessionDto.FollowupAnswer(token,1,2,List.of("A"))));
        assertEquals(1,sessions.answer(new SessionDto.Answer(token,1,"replacement",null),"127.0.0.1","test").followup().ordinal());
        jdbc.update("UPDATE challenge_platform.question_followup SET prompt='edited later',enabled=false WHERE question_id=?",first.question().id());
        assertEquals(current.followup(),sessions.state(token).followup());
        String publicJson=new ObjectMapper().findAndRegisterModules().writeValueAsString(current);
        for(String secret:List.of("expectedAnswer","expected_answer","rubric","reviewStatus","CORRECT"))assertFalse(publicJson.contains(secret));
        var next=completeFollowups(current);
        assertEquals("CODING",next.phase()); assertEquals(2,next.ordinal());
        var row=jdbc.queryForMap("SELECT id,candidate_id FROM challenge_platform.attempt WHERE challenge_session_id=?",id());
        var report=stats.attempt((Long)row.get("candidate_id"),(Long)row.get("id"));
        assertEquals(5,report.followups().size());
        assertEquals("NEEDS_REVIEW",report.followups().get(4).reviewStatus());
        verifyNoInteractions(judge);
    }

    @Test void deadlineEndsFollowupsWithoutCreatingAnotherCodeAttempt() {
        sessions.start(request,"127.0.0.1","test");
        sessions.answer(new SessionDto.Answer(token,1,"code",null),"127.0.0.1","test");
        jdbc.update("UPDATE challenge_platform.challenge_session SET started_at=started_at-interval '11 minutes',expires_at=expires_at-interval '11 minutes' WHERE id=?",id());
        var end=sessions.followup(new SessionDto.FollowupAnswer(token,1,1,List.of("late")));
        assertEquals("FINISHED",end.phase()); assertEquals("TIME_UP",end.reason()); assertEquals(1,end.submittedAnswers());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM challenge_platform.session_followup f JOIN challenge_platform.session_question r ON r.id=f.session_question_id WHERE r.session_id=? AND f.submitted_at IS NOT NULL",Integer.class,id()));
    }

    SessionDto.State debugStage() {
        sessions.start(request,"127.0.0.1","test");
        var current=sessions.answer(new SessionDto.Answer(token,1,"code",null),"127.0.0.1","test");
        while(current.followup().ordinal()<4) {
            var f=current.followup();
            var answer=f.kind().equals("DRAG_DROP")?f.options().stream().map(FollowupService.Option::id).toList():
                f.kind().equals("MCQ")?List.of(f.options().get(0).id()):List.of("answer");
            current=sessions.followup(new SessionDto.FollowupAnswer(token,1,f.ordinal(),answer));
        }
        assertEquals("DEBUG",current.followup().kind()); assertNotNull(current.followup().debug());
        return current;
    }
    long debugId() { return jdbc.queryForObject("SELECT f.id FROM challenge_platform.session_followup f JOIN challenge_platform.session_question r ON r.id=f.session_question_id WHERE r.session_id=? AND f.kind='DEBUG'",Long.class,id()); }
    SessionDto.FollowupAnswer debugAnswer(SessionDto.State current) {
        var debug=current.followup().debug();
        String edited=debug.starterCode()+"\n// Candidate edit";
        return new SessionDto.FollowupAnswer(token,1,4,List.of(edited),debug.prefix()+edited+debug.suffix());
    }
    @Test void optionalAnswersAdvanceWholeCycleWithoutJudgingOrResettingClock() {
        var first=sessions.start(request,"127.0.0.1","test");
        var current=sessions.answer(new SessionDto.Answer(token,1,"",null),"127.0.0.1","test");
        assertEquals("FOLLOW_UP",current.phase());
        for(int i=1;i<=5;i++) {
            var skip=new SessionDto.FollowupAnswer(token,1,i,List.of());
            current=sessions.followup(skip);
            assertEquals(current.followup(),sessions.followup(skip).followup());
            assertEquals(first.expiresAt(),current.expiresAt());
        }
        assertEquals("CODING",current.phase()); assertEquals(2,current.ordinal());
        assertEquals(5,jdbc.queryForObject("SELECT count(*) FROM challenge_platform.session_followup f JOIN challenge_platform.session_question r ON r.id=f.session_question_id WHERE r.session_id=? AND f.review_status='SKIPPED' AND f.answer='[]'::jsonb AND f.submitted_at IS NOT NULL AND f.debug_source IS NULL",Integer.class,id()));
        sessions.answer(new SessionDto.Answer(token,2,current.question().starterCode(),null),"127.0.0.1","test");
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM challenge_platform.attempt WHERE challenge_session_id=? AND judge_status='SKIPPED' AND score=0",Integer.class,id()));
        submissions.gradeNextQueued(); followups.gradeNextQueued();
        verifyNoInteractions(judge);
    }
    @Test void unchangedDebugIsSkippedAndPartialPlacementIsStoredAsIncorrect() {
        var current=sessions.start(request,"127.0.0.1","test");
        current=sessions.answer(new SessionDto.Answer(token,1,"",null),"127.0.0.1","test");
        while(!current.followup().kind().equals("DRAG_DROP"))
            current=sessions.followup(new SessionDto.FollowupAnswer(token,1,current.followup().ordinal(),List.of()));
        var partial=new ArrayList<>(Collections.nCopies(current.followup().options().size(),""));
        partial.set(0,current.followup().options().get(0).id());
        current=sessions.followup(new SessionDto.FollowupAnswer(token,1,current.followup().ordinal(),partial));
        assertEquals("DEBUG",current.followup().kind());
        var debug=current.followup().debug();
        sessions.followup(new SessionDto.FollowupAnswer(token,1,4,List.of(debug.starterCode()),debug.prefix()+debug.starterCode()+debug.suffix()));
        assertEquals("SKIPPED",jdbc.queryForObject("SELECT review_status FROM challenge_platform.session_followup WHERE id=?",String.class,debugId()));
        assertEquals("INCORRECT",jdbc.queryForObject("SELECT f.review_status FROM challenge_platform.session_followup f JOIN challenge_platform.session_question r ON r.id=f.session_question_id WHERE r.session_id=? AND f.kind='DRAG_DROP'",String.class,id()));
        followups.gradeNextQueued(); verifyNoInteractions(judge);
    }
    @Test void executableDebugIsSnapshottedQueuedIdempotentlyAndGradedPrivately() throws Exception {
        var current=debugStage();
        var request=debugAnswer(current);
        String publicJson=new ObjectMapper().findAndRegisterModules().writeValueAsString(current.followup());
        for(String field:List.of("debug_tests","expectedOutput","debugResult","expectedAnswer","rubric"))assertFalse(publicJson.contains(field),field);
        assertThrows(ResponseStatusException.class,()->sessions.followup(new SessionDto.FollowupAnswer(token,1,4,request.answers(),"tampered wrapper")));
        assertEquals("UNANSWERED",jdbc.queryForObject("SELECT review_status FROM challenge_platform.session_followup WHERE id=?",String.class,debugId()));
        // Authoring changes cannot replace the issued wrapper or tests.
        jdbc.update("UPDATE challenge_platform.question_followup SET debug_config=null,debug_tests=null WHERE question_id=? AND kind='DEBUG'",current.question().id());
        var next=sessions.followup(request);
        assertEquals(5,next.followup().ordinal()); assertEquals(current.expiresAt(),next.expiresAt());
        assertEquals(next.followup(),sessions.followup(request).followup());
        verifyNoInteractions(judge);
        assertEquals("QUEUED",jdbc.queryForObject("SELECT review_status FROM challenge_platform.session_followup WHERE id=?",String.class,debugId()));
        when(judge.execute(anyString(),anyInt(),anyString(),anyString())).thenReturn(new Judge0Client.Execution(3,"Accepted","ok",null,2,10));
        followups.gradeNextQueued();
        assertEquals("CORRECT",jdbc.queryForObject("SELECT review_status FROM challenge_platform.session_followup WHERE id=?",String.class,debugId()));
        verify(judge,times(10)).execute(eq(request.sourceCode()),eq(62),anyString(),anyString());
        followups.gradeNextQueued();
        verifyNoMoreInteractions(judge);
        var row=jdbc.queryForMap("SELECT id,candidate_id FROM challenge_platform.attempt WHERE challenge_session_id=?",id());
        var review=stats.attempt((Long)row.get("candidate_id"),(Long)row.get("id")).followups().get(3);
        assertEquals(10,review.debugResult().passed()); assertEquals(10,review.debugResult().cases().size());
        assertEquals(request.sourceCode(),review.debugSource());
    }
    @Test void debugJudgeOutageRetriesThenRemainsUnavailableNotIncorrect() {
        var current=debugStage(); sessions.followup(debugAnswer(current));
        when(judge.execute(anyString(),anyInt(),anyString(),anyString())).thenReturn(new Judge0Client.Execution(-1,"unavailable",null,null,null,null));
        for(int i=0;i<3;i++) {
            jdbc.update("UPDATE challenge_platform.session_followup SET grading_next_at=NULL WHERE id=?",debugId());
            followups.gradeNextQueued();
        }
        assertEquals("GRADING_UNAVAILABLE",jdbc.queryForObject("SELECT review_status FROM challenge_platform.session_followup WHERE id=?",String.class,debugId()));
        assertNull(jdbc.queryForObject("SELECT debug_result FROM challenge_platform.session_followup WHERE id=?",String.class,debugId()));
        assertEquals(5,sessions.state(token).followup().ordinal());
    }
    @Test void wrongDebugAnswerStoresJudgeFailureWithoutChangingCodingScore() {
        var current=debugStage(); sessions.followup(debugAnswer(current));
        when(judge.execute(anyString(),anyInt(),anyString(),anyString())).thenReturn(new Judge0Client.Execution(4,"Wrong Answer","bad",null,1,10));
        followups.gradeNextQueued();
        assertEquals("INCORRECT",jdbc.queryForObject("SELECT review_status FROM challenge_platform.session_followup WHERE id=?",String.class,debugId()));
        assertEquals(0,jdbc.queryForObject("SELECT (debug_result->>'passed')::int FROM challenge_platform.session_followup WHERE id=?",Integer.class,debugId()));
        assertEquals("QUEUED",jdbc.queryForObject("SELECT judge_status FROM challenge_platform.attempt WHERE challenge_session_id=?",String.class,id()));
    }

    @Test void eligibilityRequiresAllFiveEnabledFollowupsAndDoesNotDeleteQuestions() {
        int stored=jdbc.queryForObject("SELECT count(*) FROM challenge_platform.question",Integer.class);
        jdbc.update("UPDATE challenge_platform.question_followup SET enabled=false WHERE ordinal=5");
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM challenge_platform.followup_eligible_question",Integer.class));
        assertThrows(ResponseStatusException.class,()->sessions.start(request,"127.0.0.1","test"));
        assertEquals(stored,jdbc.queryForObject("SELECT count(*) FROM challenge_platform.question",Integer.class));
    }

    @Test void exhaustedQuestionBankFinishesWithoutRepeatsOrExtraAnswers() {
        var current=sessions.start(request,"127.0.0.1","test");
        var ids=new HashSet<Long>(); int count=0;
        while(current.status().equals("ACTIVE")) {
            assertTrue(ids.add(current.question().id()));
            current=sessions.answer(new SessionDto.Answer(token,current.ordinal(),"answer",null),"127.0.0.1","test");
            current=completeFollowups(current);
            assertTrue(++count<100);
        }
        assertEquals("ALL_QUESTIONS_SUBMITTED",current.reason());
        assertEquals(count,current.submittedAnswers());
        assertEquals(count,sessions.finish(token).submittedAnswers());
        verifyNoInteractions(judge);
    }

    @Test void resetRevokesOldSessionButKeepsSubmittedAnswers() {
        assertFalse(sessions.start(request,"127.0.0.1","test").restartAllowed());
        sessions.answer(new SessionDto.Answer(token,1,"saved answer",null),"127.0.0.1","test");
        resets.reset(List.of(request.email()),"admin@codereport.com");
        var old=sessions.state(token);
        assertEquals("RESET",old.reason()); assertEquals(1,old.submittedAnswers());
        assertTrue(old.restartAllowed());
        assertTrue(sessions.start(request,"127.0.0.1","test").restartAllowed()); // Old tokens remain idempotent.
        assertEquals("FINISHED",sessions.answer(new SessionDto.Answer(token,1,"revoked",null),"127.0.0.1","test").status());
        var fresh=sessions.start(new SessionDto.Start(UUID.randomUUID().toString(),request.fullName(),request.email(),request.phone(),"test"),"127.0.0.1","test");
        assertEquals("ACTIVE",fresh.status()); assertEquals(0,fresh.submittedAnswers());
        assertFalse(fresh.restartAllowed());
        assertFalse(sessions.state(token).restartAllowed()); // The new daily slot is occupied.
    }

    @Test void resettingCompletedSessionAllowsRetestWithoutChangingItsHistory() {
        sessions.start(request,"127.0.0.1","test");
        sessions.answer(new SessionDto.Answer(token,1,"saved answer",null),"127.0.0.1","test");
        var completed=sessions.finish(token);
        assertFalse(completed.restartAllowed());
        long oldId=id();
        resets.reset(List.of(request.email()),"admin@codereport.com");
        var reset=sessions.state(token);
        assertTrue(reset.restartAllowed());
        assertEquals(completed.reason(),reset.reason());
        assertEquals(completed.finishedAt(),reset.finishedAt());
        assertEquals(1,reset.submittedAnswers());
        var fresh=sessions.start(new SessionDto.Start(UUID.randomUUID().toString(),request.fullName(),request.email(),request.phone(),"test"),"127.0.0.1","test");
        assertEquals("ACTIVE",fresh.status());
        assertEquals(Duration.ofMinutes(10),Duration.between(fresh.startedAt(),fresh.expiresAt()));
        assertEquals("saved answer",jdbc.queryForObject("SELECT source_code FROM challenge_platform.attempt WHERE challenge_session_id=?",String.class,oldId));
        assertFalse(sessions.state(token).restartAllowed());
    }

    @Test void unavailableJudgeIsRetriedWithoutDuplicatingCaseResults() {
        sessions.start(request,"127.0.0.1","test");
        sessions.answer(new SessionDto.Answer(token,1,"saved answer",null),"127.0.0.1","test");
        when(judge.execute(anyString(),anyInt(),nullable(String.class),nullable(String.class)))
            .thenReturn(new Judge0Client.Execution(3,"Accepted","",null,1,1))
            .thenReturn(new Judge0Client.Execution(-1,"Unavailable",null,null,null,null));
        for(int i=0;i<3;i++) {
            jdbc.update("UPDATE challenge_platform.attempt SET grading_next_at=NULL WHERE challenge_session_id=?",id());
            submissions.gradeNextQueued();
        }
        assertEquals("GRADING_UNAVAILABLE",jdbc.queryForObject("SELECT judge_status FROM challenge_platform.attempt WHERE challenge_session_id=?",String.class,id()));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM challenge_platform.attempt_result r JOIN challenge_platform.attempt a ON a.id=r.attempt_id WHERE a.challenge_session_id=?",Integer.class,id()));
    }
}
