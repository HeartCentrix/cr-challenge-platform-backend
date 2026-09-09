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

    @Test void exhaustedQuestionBankFinishesWithoutRepeatsOrExtraAnswers() {
        var current=sessions.start(request,"127.0.0.1","test");
        var ids=new HashSet<Long>(); int count=0;
        while(current.status().equals("ACTIVE")) {
            assertTrue(ids.add(current.question().id()));
            current=sessions.answer(new SessionDto.Answer(token,current.ordinal(),"answer",null),"127.0.0.1","test");
            assertTrue(++count<100);
        }
        assertEquals("ALL_QUESTIONS_SUBMITTED",current.reason());
        assertEquals(count,current.submittedAnswers());
        assertEquals(count,sessions.finish(token).submittedAnswers());
        verifyNoInteractions(judge);
    }

    @Test void resetRevokesOldSessionButKeepsSubmittedAnswers() {
        sessions.start(request,"127.0.0.1","test");
        sessions.answer(new SessionDto.Answer(token,1,"saved answer",null),"127.0.0.1","test");
        resets.reset(List.of(request.email()),"admin@codereport.com");
        var old=sessions.state(token);
        assertEquals("RESET",old.reason()); assertEquals(1,old.submittedAnswers());
        assertEquals("FINISHED",sessions.answer(new SessionDto.Answer(token,2,"revoked",null),"127.0.0.1","test").status());
        var fresh=sessions.start(new SessionDto.Start(UUID.randomUUID().toString(),request.fullName(),request.email(),request.phone(),"test"),"127.0.0.1","test");
        assertEquals("ACTIVE",fresh.status()); assertEquals(0,fresh.submittedAnswers());
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
