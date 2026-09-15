package com.qfion.challenge.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import com.qfion.challenge.dto.SessionDto;
import static org.junit.jupiter.api.Assertions.*;

class FollowupServiceTest {
    @Test void requestValidationAllowsSkippedAnswersButNotMissingPayloads() {
        try(var factory=jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var validator=factory.getValidator();
            String token=java.util.UUID.randomUUID().toString();
            assertTrue(validator.validate(new SessionDto.Answer(token,1,"",null)).isEmpty());
            assertTrue(validator.validate(new SessionDto.FollowupAnswer(token,1,1,List.of())).isEmpty());
            assertTrue(validator.validate(new SessionDto.FollowupAnswer(token,1,3,List.of("","A",""))).isEmpty());
            assertFalse(validator.validate(new SessionDto.Answer(token,1,null,null)).isEmpty());
            assertFalse(validator.validate(new SessionDto.FollowupAnswer(token,1,1,null)).isEmpty());
        }
    }
    FollowupService.PublicQuestion question(String kind) {
        return new FollowupService.PublicQuestion(1,kind,"Prompt",List.of(
            new FollowupService.Option("A","First"),new FollowupService.Option("B","Second"),new FollowupService.Option("C","Third")));
    }
    @Test void mcqAcceptsOnlyOneKnownId() {
        assertDoesNotThrow(()->FollowupService.validate(question("MCQ"),List.of("B")));
        assertThrows(ResponseStatusException.class,()->FollowupService.validate(question("MCQ"),List.of("unknown")));
        assertThrows(ResponseStatusException.class,()->FollowupService.validate(question("MCQ"),List.of("A","B")));
    }
    @Test void placementAllowsEmptySlotsButNotDuplicatesOrUnknownPieces() {
        assertDoesNotThrow(()->FollowupService.validate(question("DRAG_DROP"),List.of("","A","")));
        assertDoesNotThrow(()->FollowupService.validate(question("DRAG_DROP"),List.of("C","A","B")));
        for(var a:List.of(List.of("A","A","B"),List.of("A","B"),List.of("A","B","outside")))
            assertThrows(ResponseStatusException.class,()->FollowupService.validate(question("DRAG_DROP"),a));
    }
    @Test void answersAreOptionalButBoundedAndNonNull() {
        for(var kind:List.of("FIB","DEBUG","SHORT_ANSWER","MCQ","DRAG_DROP")) {
            assertDoesNotThrow(()->FollowupService.validate(question(kind),List.of()));
            assertThrows(ResponseStatusException.class,()->FollowupService.validate(question(kind),null));
            assertThrows(ResponseStatusException.class,()->FollowupService.validate(question(kind),java.util.Arrays.asList((String)null)));
        }
        for(var kind:List.of("FIB","DEBUG","SHORT_ANSWER")) {
            assertDoesNotThrow(()->FollowupService.validate(question(kind),List.of("answer")));
            assertDoesNotThrow(()->FollowupService.validate(question(kind),List.of(" ")));
            assertThrows(ResponseStatusException.class,()->FollowupService.validate(question(kind),List.of("x".repeat(2001))));
        }
    }
    @Test void debugRequiresTheExactIssuedWrapperAndCandidateSnippet() {
        var debug=new FollowupService.DebugConfig("return 0;","class Main { int solve(){","}}",62);
        var q=new FollowupService.PublicQuestion(4,"DEBUG","Fix",List.of(),debug);
        var answers=List.of("return 1;");
        String source=debug.prefix()+answers.get(0)+debug.suffix();
        assertEquals(source,FollowupService.validatedDebugSource(q,new SessionDto.FollowupAnswer("token",1,4,answers,source)));
        for(String invalid:List.of("return 1;",debug.prefix()+"return 2;"+debug.suffix(),source+"// changed"))
            assertThrows(ResponseStatusException.class,()->FollowupService.validatedDebugSource(q,new SessionDto.FollowupAnswer("token",1,4,answers,invalid)));
        assertThrows(ResponseStatusException.class,()->FollowupService.validatedDebugSource(q,new SessionDto.FollowupAnswer("token",1,4,answers)));
    }
}
