package com.qfion.challenge.controller;

import com.qfion.challenge.config.AdminCredentialsFilter;
import com.qfion.challenge.dto.AdminStatsDto.Overview;
import com.qfion.challenge.service.AdminSessionService;
import com.qfion.challenge.service.AdminStatsService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminStatsTest {
    @Test void everyStatsEndpointRequiresTheExistingAdminSession() throws Exception {
        var service = mock(AdminStatsService.class);
        var sessions = mock(AdminSessionService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new AdminStatsController(service))
                .addFilters(new AdminCredentialsFilter(mock(JdbcTemplate.class), sessions)).build();
        for (String path : List.of("", "/candidates", "/candidates/1", "/candidates/1/attempts/2")) {
            mvc.perform(get("/api/v1/admin/stats" + path)).andExpect(status().isUnauthorized())
                    .andExpect(header().string("Cache-Control", "no-store"));
            mvc.perform(get("/api/v1/admin/stats" + path).header("Authorization", "Bearer expired"))
                    .andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(service);
        when(sessions.authenticate("valid-token")).thenReturn(Optional.of("admin@example.invalid"));
        when(service.overview(any(AdminStatsService.Filters.class))).thenReturn(new Overview(0, 0, "overall", java.math.BigDecimal.ZERO, List.of()));
        mvc.perform(get("/api/v1/admin/stats").header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalCandidates").value(0))
                .andExpect(header().string("Cache-Control", "no-store"));
        verify(service).overview(any(AdminStatsService.Filters.class));
        mvc.perform(get("/api/v1/admin/stats/candidates/1").header("Authorization", "Bearer valid-token")
                .param("day", "2025-03-09").param("timeZone", "America/New_York").param("asOf", "2025-03-10T12:00:00Z"))
                .andExpect(status().isOk());
        verify(service).candidate(1, 0, 10, "2025-03-09", "America/New_York", "2025-03-10T12:00:00Z");
    }
}
