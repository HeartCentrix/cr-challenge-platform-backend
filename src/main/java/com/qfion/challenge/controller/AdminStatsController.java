package com.qfion.challenge.controller;

import com.qfion.challenge.dto.AdminStatsDto.*;
import com.qfion.challenge.service.AdminStatsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/stats")
@Tag(name = "Admin candidate statistics", description = "Requires the same bearer session as the daily-limit reset admin.")
@RequiredArgsConstructor
public class AdminStatsController {
    private final AdminStatsService stats;

    @GetMapping
    public Overview overview(@RequestAttribute("authenticatedAdmin") String admin,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "") String campaign,
            @RequestParam(defaultValue = "") String startDate, @RequestParam(defaultValue = "") String endDate,
            @RequestParam(defaultValue = "UTC") String timeZone,
            @RequestParam(defaultValue = "0") double minPercent, @RequestParam(defaultValue = "100") double maxPercent,
            @RequestParam(defaultValue = "") String asOf) {
        return stats.overview(new AdminStatsService.Filters(search, campaign, startDate, endDate, timeZone, minPercent, maxPercent, asOf));
    }

    @GetMapping("/candidates")
    public CandidatePage list(@RequestAttribute("authenticatedAdmin") String admin,
            @RequestParam(defaultValue = "all") String bucket, @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "") String campaign,
            @RequestParam(required = false) Long afterId, @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "") String startDate, @RequestParam(defaultValue = "") String endDate,
            @RequestParam(defaultValue = "UTC") String timeZone,
            @RequestParam(defaultValue = "0") double minPercent, @RequestParam(defaultValue = "100") double maxPercent,
            @RequestParam(defaultValue = "") String asOf) {
        return stats.list(bucket, new AdminStatsService.Filters(search, campaign, startDate, endDate, timeZone, minPercent, maxPercent, asOf), afterId, size);
    }

    @GetMapping("/candidates/{id}")
    public CandidateDetail candidate(@RequestAttribute("authenticatedAdmin") String admin, @PathVariable long id,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "") String day, @RequestParam(defaultValue = "UTC") String timeZone,
            @RequestParam(defaultValue = "") String asOf) {
        return stats.candidate(id, page, size, day, timeZone, asOf);
    }

    @GetMapping("/candidates/{id}/attempts/{attemptId}")
    public AttemptDetail attempt(@RequestAttribute("authenticatedAdmin") String admin, @PathVariable long id,
            @PathVariable long attemptId) { return stats.attempt(id, attemptId); }
}
