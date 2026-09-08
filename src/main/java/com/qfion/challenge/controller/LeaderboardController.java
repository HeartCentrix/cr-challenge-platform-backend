package com.qfion.challenge.controller;

import com.qfion.challenge.dto.Dto;
import com.qfion.challenge.repo.LeaderboardRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/leaderboard")
@RequiredArgsConstructor
public class LeaderboardController {

    private final LeaderboardRepo leaderboardRepo;

    @GetMapping
    public List<Dto.LeaderboardRow> top(@RequestParam(defaultValue = "50") int limit) {
        List<Dto.LeaderboardRow> rows = new ArrayList<>();
        for (Object[] r : leaderboardRepo.topCandidates(Math.min(limit, 200))) {
            rows.add(new Dto.LeaderboardRow(
                    num(r[0]).longValue(),
                    (String) r[1]));
        }
        return rows;
    }

    private static Number num(Object o) {
        if (o == null) return BigDecimal.ZERO;
        return (o instanceof Number n) ? n : new BigDecimal(o.toString());
    }
}
