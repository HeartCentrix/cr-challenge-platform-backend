package com.qfion.challenge.controller;

import com.qfion.challenge.repo.AttemptRepo;
import com.qfion.challenge.repo.CandidateRepo;
import com.qfion.challenge.service.CandidateMapService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public counters for the landing page ticker. Deliberately aggregate only:
 * nothing here identifies a candidate.
 */
@RestController
@RequestMapping("/api/v1/stats")
@RequiredArgsConstructor
public class StatsController {

    private final AttemptRepo attemptRepo;
    private final CandidateRepo candidateRepo;
    private final CandidateMapService map;

    public record Stats(long attempts, long developers, CandidateMapService.ActivityMap activityMap) {}

    @GetMapping
    public Stats stats() {
        return new Stats(attemptRepo.count(), candidateRepo.count(), map.snapshot());
    }
}
