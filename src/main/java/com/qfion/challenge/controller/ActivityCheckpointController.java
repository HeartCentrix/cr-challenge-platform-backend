package com.qfion.challenge.controller;

import com.qfion.challenge.dto.Dto;
import com.qfion.challenge.service.ActivityCheckpointService;
import com.qfion.challenge.service.ClientIpAddress;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/activity-checkpoints")
@RequiredArgsConstructor
public class ActivityCheckpointController {
    private final ActivityCheckpointService checkpoints;
    @Value("${challenge.trusted-proxy-hops:0}") private int trustedProxyHops;
    @PostMapping
    public ResponseEntity<Void> save(@Valid @RequestBody Dto.ActivityCheckpointRequest request, HttpServletRequest http) {
        checkpoints.rateLimit(ClientIpAddress.from(http, trustedProxyHops));
        checkpoints.save(request);
        return ResponseEntity.noContent().header("Cache-Control", "no-store").build();
    }
}
