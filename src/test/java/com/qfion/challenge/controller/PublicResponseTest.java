package com.qfion.challenge.controller;

import com.qfion.challenge.dto.Dto;
import com.qfion.challenge.repo.LeaderboardRepo;
import com.qfion.challenge.service.SubmissionService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PublicResponseTest {
    @Test
    void leaderboardExposesOnlyRankAndDisplayName() throws Exception {
        LeaderboardRepo repo = mock(LeaderboardRepo.class);
        when(repo.topCandidates(50)).thenReturn(Collections.singletonList(new Object[] { 7L, "Test Candidate" }));
        MockMvcBuilders.standaloneSetup(new LeaderboardController(repo)).build()
                .perform(get("/api/v1/leaderboard"))
                .andExpect(status().isOk())
                .andExpect(content().json("[{\"rank\":7,\"displayName\":\"Test Candidate\"}]", true));
    }

    @Test
    void submissionExposesOnlyAcknowledgement() throws Exception {
        SubmissionService service = mock(SubmissionService.class);
        when(service.submit(any(Dto.SubmitRequest.class), nullable(String.class), nullable(String.class)))
                .thenReturn(new Dto.SubmitResponse("Your submission has been saved."));
        MockMvcBuilders.standaloneSetup(new SubmissionController(service)).build()
                .perform(post("/api/v1/submit").contentType(MediaType.APPLICATION_JSON).content("""
                        {"slug":"test-question","sourceCode":"class Main {}","fullName":"Test Candidate",
                         "email":"test@example.invalid","phone":"2025550196","consent":false}
                        """))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"message\":\"Your submission has been saved.\"}", true));
    }
}
