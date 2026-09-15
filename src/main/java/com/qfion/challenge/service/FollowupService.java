package com.qfion.challenge.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qfion.challenge.dto.SessionDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class FollowupService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Judge0Client judge;
    public record Option(String id,String text) {}
    public record DebugConfig(String starterCode,String prefix,String suffix,int languageId) {}
    public record DebugCase(String stdin,String expectedOutput) {}
    public record DebugCaseResult(int ordinal,String status,boolean passed,String stdout,String stderr,Integer execTimeMs) {}
    public record DebugResult(int passed,int total,List<DebugCaseResult> cases) {}
    public record PublicQuestion(int ordinal,String kind,String prompt,List<Option> options,DebugConfig debug) {
        public PublicQuestion(int ordinal,String kind,String prompt,List<Option> options) { this(ordinal,kind,prompt,options,null); }
    }
    public record Review(int ordinal,String kind,String prompt,List<Option> options,List<String> answer,
                         List<String> expectedAnswer,String rubric,String reviewStatus,OffsetDateTime submittedAt,
                         DebugConfig debug,DebugResult debugResult,String debugSource) {}

    public void snapshot(long round) {
        int count=jdbc.update("""
          INSERT INTO challenge_platform.session_followup(session_question_id,followup_id,ordinal,kind,prompt,options,expected_answer,rubric,debug_config,debug_tests)
          SELECT r.id,f.id,f.ordinal,f.kind,f.prompt,f.options,f.expected_answer,f.rubric,f.debug_config,f.debug_tests
          FROM challenge_platform.session_question r JOIN challenge_platform.question_followup f ON f.question_id=r.question_id
          WHERE r.id=? AND f.enabled ORDER BY f.ordinal
          """,round);
        if(count!=5) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"This question's follow-ups are unavailable.");
    }
    public PublicQuestion current(long round) {
        var rows=jdbc.query("SELECT ordinal,kind,prompt,options::text,debug_config::text FROM challenge_platform.session_followup WHERE session_question_id=? AND submitted_at IS NULL ORDER BY ordinal LIMIT 1",
            (rs,n)->new PublicQuestion(rs.getInt("ordinal"),rs.getString("kind"),rs.getString("prompt"),options(rs.getString("options")),read(rs.getString("debug_config"),DebugConfig.class)),round);
        return rows.isEmpty()?null:rows.get(0);
    }
    /** Caller holds the session row lock and has checked its server-side deadline. */
    public boolean answer(long round,SessionDto.FollowupAnswer request,OffsetDateTime now) {
        var rows=jdbc.queryForList("SELECT * FROM challenge_platform.session_followup WHERE session_question_id=? AND ordinal=?",round,request.followupOrdinal());
        if(rows.isEmpty())throw conflict();
        var row=rows.get(0);
        if(row.get("submitted_at")!=null)return false; // Idempotent lost-response retry.
        var active=current(round);
        if(active==null || active.ordinal()!=request.followupOrdinal())throw conflict();
        List<String> answer=request.answers();
        validate(active,answer);
        boolean blank=answer.stream().allMatch(String::isBlank);
        String source;
        if(blank) {
            if(request.sourceCode()!=null && !request.sourceCode().isBlank())throw bad();
            source=null;
        } else source=validatedDebugSource(active,request);
        boolean skipped=blank || (active.debug()!=null && active.kind().equals("DEBUG")
            && answer.get(0).strip().equals(active.debug().starterCode().strip()));
        var expected=strings(row.get("expected_answer").toString());
        String review=Set.of("SHORT_ANSWER","DEBUG").contains(active.kind())?"NEEDS_REVIEW":
            answer.stream().map(String::strip).toList().equals(expected.stream().map(String::strip).toList())?"CORRECT":"INCORRECT";
        if(source!=null) review="QUEUED";
        if(skipped) { review="SKIPPED"; answer=List.of(); source=null; }
        jdbc.update("UPDATE challenge_platform.session_followup SET answer=?::jsonb,submitted_at=?,review_status=?,debug_source=? WHERE id=?",encode(answer),now,review,source,row.get("id"));
        return true;
    }
    /** The browser assembles the source, but cannot replace the issued wrapper or language. */
    static String validatedDebugSource(PublicQuestion q,SessionDto.FollowupAnswer request) {
        if(!q.kind().equals("DEBUG") || q.debug()==null) {
            if(request.sourceCode()!=null)throw bad();
            return null; // Previously issued text-only DEBUG remains reviewable.
        }
        String expected=q.debug().prefix()+request.answers().get(0)+q.debug().suffix();
        if(!expected.equals(request.sourceCode()))throw bad();
        return expected;
    }

    /** Separate durable grading path: never holds the candidate session lock or delays answering. */
    @Transactional
    public void gradeNextQueued() {
        var rows=jdbc.queryForList("SELECT * FROM challenge_platform.session_followup WHERE review_status='QUEUED' AND (grading_next_at IS NULL OR grading_next_at<=clock_timestamp()) ORDER BY id FOR UPDATE SKIP LOCKED LIMIT 1");
        if(rows.isEmpty())return;
        var row=rows.get(0); Object id=row.get("id");
        var config=read(row.get("debug_config").toString(),DebugConfig.class);
        List<DebugCase> cases;
        try { cases=json.readValue(row.get("debug_tests").toString(),new TypeReference<>(){}); }
        catch(Exception e) { throw new IllegalStateException("Invalid DEBUG test cases",e); }
        if(cases.isEmpty())throw new IllegalStateException("DEBUG requires tests");
        int passed=0; var results=new ArrayList<DebugCaseResult>();
        for(var test:cases) {
            var result=judge.execute(row.get("debug_source").toString(),config.languageId(),test.stdin(),test.expectedOutput());
            if(result.statusId()<3 || result.statusId()>=13) {
                jdbc.update("UPDATE challenge_platform.session_followup SET grading_retries=grading_retries+1,grading_next_at=clock_timestamp()+interval '30 seconds',review_status=CASE WHEN grading_retries>=2 THEN 'GRADING_UNAVAILABLE' ELSE 'QUEUED' END WHERE id=?",id);
                return;
            }
            if(result.accepted())passed++;
            results.add(new DebugCaseResult(results.size()+1,result.statusDescription(),result.accepted(),bounded(result.stdout()),bounded(result.stderr()),result.execTimeMs()));
        }
        jdbc.update("UPDATE challenge_platform.session_followup SET debug_result=?::jsonb,review_status=?,grading_next_at=NULL WHERE id=?",
            encode(new DebugResult(passed,cases.size(),results)),passed==cases.size()?"CORRECT":"INCORRECT",id);
    }
    private static String bounded(String text) { return text==null?null:text.substring(0,Math.min(text.length(),4000)); }
    static void validate(PublicQuestion q,List<String> answer) {
        if(answer==null || answer.size()>10 || answer.stream().anyMatch(s->s==null||s.length()>2000))throw bad();
        if(answer.isEmpty())return;
        var filled=answer.stream().filter(s->!s.isBlank()).toList();
        if(q.kind().equals("DRAG_DROP")) {
            if(answer.size()!=q.options().size() || new HashSet<>(filled).size()!=filled.size())throw bad();
        } else if(answer.size()!=1)throw bad();
        if(q.kind().equals("MCQ")||q.kind().equals("DRAG_DROP")) {
            var ids=q.options().stream().map(Option::id).toList();
            if(!ids.containsAll(filled))throw bad();
        }
    }
    public List<Review> review(long attempt) {
        return jdbc.query("""
          SELECT f.* FROM challenge_platform.session_followup f JOIN challenge_platform.session_question r ON r.id=f.session_question_id
          WHERE r.attempt_id=? ORDER BY f.ordinal
          """,(rs,n)->new Review(rs.getInt("ordinal"),rs.getString("kind"),rs.getString("prompt"),options(rs.getString("options")),
            rs.getString("answer")==null?List.of():strings(rs.getString("answer")),strings(rs.getString("expected_answer")),
            rs.getString("rubric"),rs.getString("review_status"),rs.getObject("submitted_at",OffsetDateTime.class),
            read(rs.getString("debug_config"),DebugConfig.class),read(rs.getString("debug_result"),DebugResult.class),rs.getString("debug_source")),attempt);
    }
    private <T> T read(String value,Class<T> type){try{return value==null?null:json.readValue(value,type);}catch(Exception e){throw new IllegalStateException("Invalid DEBUG configuration",e);}}
    private List<Option> options(String s){try{return json.readValue(s,new TypeReference<>(){});}catch(Exception e){throw new IllegalStateException("Invalid follow-up options",e);}}
    private List<String> strings(String s){try{return json.readValue(s,new TypeReference<>(){});}catch(Exception e){throw new IllegalStateException("Invalid follow-up answer",e);}}
    private String encode(Object o){try{return json.writeValueAsString(o);}catch(Exception e){throw new IllegalStateException(e);}}
    private static ResponseStatusException conflict(){return new ResponseStatusException(HttpStatus.CONFLICT,"Complete the current follow-up first.");}
    private static ResponseStatusException bad(){return new ResponseStatusException(HttpStatus.BAD_REQUEST,"Provide a valid answer for this follow-up.");}
}
