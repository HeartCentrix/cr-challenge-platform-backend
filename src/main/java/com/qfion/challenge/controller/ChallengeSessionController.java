package com.qfion.challenge.controller;

import com.qfion.challenge.dto.SessionDto;
import com.qfion.challenge.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/challenge-sessions")
@RequiredArgsConstructor
public class ChallengeSessionController {
    private final ChallengeSessionService sessions;
    private final ActivityCheckpointService limits;
    @Value("${challenge.trusted-proxy-hops:0}") private int trustedProxyHops;
    private String ip(HttpServletRequest http) { return ClientIpAddress.from(http, trustedProxyHops); }
    private ResponseEntity<SessionDto.State> response(SessionDto.State state) { return ResponseEntity.ok().header("Cache-Control","no-store").body(state); }
    @PostMapping("/start") public ResponseEntity<SessionDto.State> start(@Valid @RequestBody SessionDto.Start req, HttpServletRequest http) {
        limits.rateLimit(ip(http)); return response(sessions.start(req,ip(http),http.getHeader("User-Agent")));
    }
    @PostMapping("/state") public ResponseEntity<SessionDto.State> state(@Valid @RequestBody SessionDto.Access req, HttpServletRequest http) {
        limits.rateLimit(ip(http)); return response(sessions.state(req.token()));
    }
    @PostMapping("/answer") public ResponseEntity<SessionDto.State> answer(@Valid @RequestBody SessionDto.Answer req, HttpServletRequest http) {
        limits.rateLimit(ip(http)); return response(sessions.answer(req,ip(http),http.getHeader("User-Agent")));
    }
    @PostMapping("/draft") public ResponseEntity<Void> draft(@Valid @RequestBody SessionDto.Draft req, HttpServletRequest http) {
        limits.rateLimit(ip(http)); sessions.draft(req); return ResponseEntity.noContent().header("Cache-Control","no-store").build();
    }
    @PostMapping("/finish") public ResponseEntity<SessionDto.State> finish(@Valid @RequestBody SessionDto.Access req, HttpServletRequest http) {
        limits.rateLimit(ip(http)); return response(sessions.finish(req.token()));
    }
}
