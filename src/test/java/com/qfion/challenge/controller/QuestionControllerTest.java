package com.qfion.challenge.controller;

import com.qfion.challenge.entity.Question;
import com.qfion.challenge.entity.Testcase;
import com.qfion.challenge.repo.QuestionRepo;
import com.qfion.challenge.repo.TestcaseRepo;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class QuestionControllerTest {
    @Test
    void returnsOnlyTheSelectedQuestionAndDoesNotCacheIt() throws Exception {
        QuestionRepo repo = mock(QuestionRepo.class);
        TestcaseRepo testcases = mock(TestcaseRepo.class);
        Question question = Question.builder().id(7L).slug("random-question").title("Random question")
                .difficulty(4).language("java").timeLimitSeconds(1200)
                .prompt("Solve this problem").judgeLanguageId(62).starterCode("class Main {}")
                .referenceSolution("must stay private").build();
        when(repo.findRandomActive()).thenReturn(Optional.of(question));
        when(testcases.findByQuestionIdAndIsSampleTrueOrderByOrdinalAsc(7L)).thenReturn(List.of(
                Testcase.builder().stdin("1 2").expectedOutput("3").isSample(true).build()));

        MockMvcBuilders.standaloneSetup(new QuestionController(repo, testcases)).build()
                .perform(get("/api/v1/questions"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().json("""
                        {"id":7,"slug":"random-question","title":"Random question",
                         "prompt":"Solve this problem","difficulty":4,"language":"java",
                         "judgeLanguageId":62,"starterCode":"class Main {}","timeLimitSeconds":1200,
                         "samples":[{"stdin":"1 2","expectedOutput":"3"}]}
                        """, true));
        verify(repo).findRandomActive();
        verifyNoMoreInteractions(repo);
        verify(testcases).findByQuestionIdAndIsSampleTrueOrderByOrdinalAsc(7L);
        verifyNoMoreInteractions(testcases);
    }

    @Test
    void returnsNotFoundWhenNoQuestionsAreActive() throws Exception {
        QuestionRepo repo = mock(QuestionRepo.class);
        TestcaseRepo testcases = mock(TestcaseRepo.class);
        when(repo.findRandomActive()).thenReturn(Optional.empty());
        MockMvcBuilders.standaloneSetup(new QuestionController(repo, testcases)).build()
                .perform(get("/api/v1/questions"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(""));
        verifyNoInteractions(testcases);
    }
}
