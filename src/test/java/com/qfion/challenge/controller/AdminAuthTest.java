package com.qfion.challenge.controller;

import com.qfion.challenge.config.AdminCredentialsFilter;
import com.qfion.challenge.service.AdminSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import java.util.Base64;
import java.util.Optional;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminAuthTest {
    @Test void loginRequiresPasswordAndLogoutRevokesSession() throws Exception {
        var jdbc = mock(JdbcTemplate.class);
        var sessions = mock(AdminSessionService.class);
        when(jdbc.queryForList(anyString(), eq(String.class), eq("admin@example.invalid")))
                .thenReturn(List.of(BCrypt.hashpw("test-password", BCrypt.gensalt(4))));
        when(sessions.create("admin@example.invalid")).thenReturn(new AdminSessionService.LoginResponse(
                "opaque-token", "admin@example.invalid", OffsetDateTime.now().plusMinutes(30)));
        when(sessions.authenticate("opaque-token")).thenReturn(Optional.of("admin@example.invalid"));
        var mvc = MockMvcBuilders.standaloneSetup(new AdminAuthController(sessions))
                .addFilters(new AdminCredentialsFilter(jdbc, sessions)).build();
        String basic = "Basic " + Base64.getEncoder().encodeToString("admin@example.invalid:test-password".getBytes(StandardCharsets.UTF_8));
        mvc.perform(post("/api/v1/admin/auth/login")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/admin/auth/login").header("Authorization", "Basic " + Base64.getEncoder()
                .encodeToString("admin@example.invalid:wrong".getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isUnauthorized());
        verify(sessions, never()).create(anyString());
        mvc.perform(post("/api/v1/admin/auth/login").header("Authorization", basic))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token").value("opaque-token"))
                .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(post("/api/v1/admin/auth/session").header("Authorization", basic)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/admin/auth/session").header("Authorization", "Bearer opaque-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("admin@example.invalid"));
        mvc.perform(post("/api/v1/admin/auth/logout").header("Authorization", "Bearer opaque-token"))
                .andExpect(status().isOk());
        verify(sessions).revoke("opaque-token");
    }
}
