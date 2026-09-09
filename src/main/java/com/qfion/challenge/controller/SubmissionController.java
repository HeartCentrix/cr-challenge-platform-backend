package com.qfion.challenge.controller;

import com.qfion.challenge.dto.Dto;
import com.qfion.challenge.service.SubmissionService;
import com.qfion.challenge.service.ClientIpAddress;
import org.springframework.beans.factory.annotation.Value;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionService submissionService;
    @Value("${challenge.trusted-proxy-hops:0}")
    private int trustedProxyHops;

    @PostMapping("/run")
    public Dto.RunResult run(@Valid @RequestBody Dto.RunRequest req) {
        return submissionService.run(req);
    }

    @PostMapping("/submit")
    public ResponseEntity<?> submit(@Valid @RequestBody Dto.SubmitRequest req, HttpServletRequest http) {
        try {
            return ResponseEntity.ok(submissionService.submit(req, clientIp(http), http.getHeader("User-Agent")));
        } catch (SubmissionService.AlreadyPlayedTodayException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of("error", e.getMessage()));
        } catch (SubmissionService.QuestionNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    private String clientIp(HttpServletRequest req) {
        return ClientIpAddress.from(req, trustedProxyHops);
    }
}
