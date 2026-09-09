package com.qfion.challenge.controller;

import com.qfion.challenge.dto.Dto;
import com.qfion.challenge.service.SubmissionService;
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

    @PostMapping("/run")
    public Dto.RunResult run(@Valid @RequestBody Dto.RunRequest req) {
        return submissionService.run(req);
    }

    @PostMapping("/submit")
    public ResponseEntity<?> submit(@Valid @RequestBody Dto.SubmitRequest req, HttpServletRequest http) {
        // A legacy direct submission must not bypass the global session deadline.
        return ResponseEntity.status(HttpStatus.GONE).body(Map.of("error", "Please reload and start a 10-minute challenge session."));
    }

}
