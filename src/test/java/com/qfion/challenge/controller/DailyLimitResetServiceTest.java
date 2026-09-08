package com.qfion.challenge.controller;

import com.qfion.challenge.entity.Candidate;
import com.qfion.challenge.repo.CandidateRepo;
import com.qfion.challenge.service.DailyLimitResetService;
import com.qfion.challenge.service.IdentityService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DailyLimitResetServiceTest {
    @Test void deduplicatesNormalisedEmailsAndDeletesOnlySelectedCandidatesToday() {
        var jdbc = mock(JdbcTemplate.class);
        var candidates = mock(CandidateRepo.class);
        when(candidates.findByEmailNormalised("admin@gmail.com"))
                .thenReturn(Optional.of(Candidate.builder().id(42L).build()));
        when(candidates.findByEmailNormalised("missing@example.invalid")).thenReturn(Optional.empty());
        when(jdbc.update(startsWith("DELETE"), eq(42L), any(LocalDate.class))).thenReturn(2);
        var result = new DailyLimitResetService(jdbc, candidates, new IdentityService("test-salt"))
                .reset(List.of("Ad.min+test@gmail.com", "admin@gmail.com", "missing@example.invalid"), "actor@example.invalid");
        assertEquals(2, result.results().size());
        assertEquals("RESET", result.results().get(0).status());
        assertEquals("NOT_FOUND", result.results().get(1).status());
        verify(jdbc).update("DELETE FROM challenge_platform.daily_attempt_lock WHERE candidate_id = ? AND attempt_date = ?", 42L, result.date());
        verify(jdbc).update(startsWith("INSERT INTO challenge_platform_admin.daily_limit_reset_audit"),
                eq("actor@example.invalid"), eq(result.date()), eq(42L), eq(2));
        verify(candidates, times(1)).findByEmailNormalised("admin@gmail.com");
    }
}
