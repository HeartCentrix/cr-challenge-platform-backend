package com.qfion.challenge.controller;

import com.qfion.challenge.service.AdminSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {
    private final AdminSessionService sessions;

    @PostMapping("/login")
    public AdminSessionService.LoginResponse login(@RequestAttribute("authenticatedAdmin") String email) {
        return sessions.create(email);
    }

    @PostMapping("/session")
    public Map<String, String> session(@RequestAttribute("authenticatedAdmin") String email) {
        return Map.of("email", email);
    }

    @PostMapping("/logout")
    public Map<String, String> logout(@RequestAttribute("adminSessionToken") String token) {
        sessions.revoke(token);
        return Map.of("message", "Signed out.");
    }
}
