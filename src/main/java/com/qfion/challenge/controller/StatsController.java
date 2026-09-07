package com.qfion.challenge.controller;

import com.qfion.challenge.repo.AttemptRepo;
import com.qfion.challenge.repo.CandidateRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

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

    @GetMapping
    public Map<String, Long> stats() {
        return Map.of(
                "attempts", attemptRepo.count(),
                "developers", candidateRepo.count());
    }
}
