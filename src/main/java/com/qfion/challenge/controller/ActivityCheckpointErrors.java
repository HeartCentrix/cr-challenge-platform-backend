package com.qfion.challenge.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;
import java.util.Map;

/** Never echo source code or session capabilities in validation logs/responses. */
@RestControllerAdvice(assignableTypes = {ActivityCheckpointController.class, ChallengeSessionController.class})
public class ActivityCheckpointErrors {
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String, String>> invalid(Exception error) {
        return ResponseEntity.badRequest().header("Cache-Control", "no-store")
                .body(Map.of("error", "Invalid challenge request."));
    }
}
