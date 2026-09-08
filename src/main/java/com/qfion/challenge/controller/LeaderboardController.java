package com.qfion.challenge.controller;

import com.qfion.challenge.dto.Dto;
import com.qfion.challenge.repo.LeaderboardRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/v1/leaderboard")
@RequiredArgsConstructor
public class LeaderboardController {

    private static final Pattern NAME_PARTS = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

    private final LeaderboardRepo leaderboardRepo;

    @GetMapping
    public List<Dto.LeaderboardRow> top(@RequestParam(defaultValue = "50") int limit) {
        List<Dto.LeaderboardRow> rows = new ArrayList<>();
        for (Object[] r : leaderboardRepo.topCandidates(Math.min(limit, 200))) {
            rows.add(new Dto.LeaderboardRow(
                    num(r[0]).longValue(),
                    publicName((String) r[1])));
        }
        return rows;
    }

    private static Number num(Object o) {
        if (o == null) return BigDecimal.ZERO;
        return (o instanceof Number n) ? n : new BigDecimal(o.toString());
    }

    private static String publicName(String fullName) {
        if (fullName == null) return "Anonymous";
        String normalized = NAME_PARTS.matcher(fullName).replaceAll(" ").trim();
        if (normalized.isEmpty()) return "Anonymous";
        String[] parts = normalized.split(" ");
        if (parts.length == 1) return parts[0];
        String lastName = parts[parts.length - 1];
        String initial = lastName.substring(0, lastName.offsetByCodePoints(0, 1)).toUpperCase(Locale.ROOT);
        return parts[0] + " " + initial + ".";
    }
}
