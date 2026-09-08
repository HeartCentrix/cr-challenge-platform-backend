package com.qfion.challenge.controller;

import com.qfion.challenge.config.AdminCredentialsFilter;
import com.qfion.challenge.service.DailyLimitResetService;
import com.qfion.challenge.service.AdminSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminResetTest {
    private DailyLimitResetService service;
    private MockMvc mvc;
    private String auth;

    @BeforeEach
    void setup() {
        service = mock(DailyLimitResetService.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(String.class), eq("admin@example.invalid")))
                .thenReturn(List.of(BCrypt.hashpw("test-password", BCrypt.gensalt(4))));
        when(service.reset(anyList(), anyString())).thenReturn(new DailyLimitResetService.ResetResult(
                LocalDate.now(), List.of(new DailyLimitResetService.EmailResult("candidate@example.invalid", "RESET", 2))));
        var sessions = mock(AdminSessionService.class);
        when(sessions.authenticate("test-session")).thenReturn(java.util.Optional.of("admin@example.invalid"));
        mvc = MockMvcBuilders.standaloneSetup(new AdminResetController(service))
                .addFilters(new AdminCredentialsFilter(jdbc, sessions)).build();
        auth = "Bearer test-session";
    }

    @Test void rejectsMissingAndWrongCredentials() throws Exception {
        mvc.perform(post("/api/v1/admin/daily-limit/reset").contentType(MediaType.APPLICATION_JSON)
                .content("{\"emails\":[\"candidate@example.invalid\"],\"confirmed\":true}"))
                .andExpect(status().isUnauthorized()).andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(post("/api/v1/admin/daily-limit/reset").header("Authorization", "Basic malformed"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/admin/daily-limit/reset").header("Authorization", "Basic " + Base64.getEncoder()
                .encodeToString("admin@example.invalid:incorrect".getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test void resetsWithAuthenticatedActorAndConfirmation() throws Exception {
        mvc.perform(post("/api/v1/admin/daily-limit/reset").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emails\":[\"candidate@example.invalid\"],\"confirmed\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.results[0].status").value("RESET"));
        verify(service).reset(List.of("candidate@example.invalid"), "admin@example.invalid");
    }

    @Test void rejectsInvalidEmailsMissingConfirmationAndOversizedBatches() throws Exception {
        for (String json : List.of("{\"emails\":[\"invalid\"],\"confirmed\":true}",
                "{\"emails\":[],\"confirmed\":true}", "{\"emails\":[\"candidate@example.invalid\"],\"confirmed\":false}",
                "{\"emails\":[\"candidate@example.invalid\"]}",
                "{\"emails\":[" + String.join(",", java.util.Collections.nCopies(101, "\"candidate@example.invalid\"")) + "],\"confirmed\":true}")) {
            mvc.perform(post("/api/v1/admin/daily-limit/reset").header("Authorization", auth)
                    .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }
}
