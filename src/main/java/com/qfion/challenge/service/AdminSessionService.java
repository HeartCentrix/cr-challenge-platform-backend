package com.qfion.challenge.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AdminSessionService {
    private final JdbcTemplate jdbc;
    private final SecureRandom random = new SecureRandom();
    public record LoginResponse(String token, String email, OffsetDateTime expiresAt) {}

    @Transactional
    public LoginResponse create(String email) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        OffsetDateTime expires = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(30);
        jdbc.update("DELETE FROM challenge_platform_admin.admin_session WHERE expires_at <= now()");
        jdbc.update("INSERT INTO challenge_platform_admin.admin_session(token_hash, admin_email, expires_at) VALUES (?, ?, ?)",
                hash(token), email, expires);
        return new LoginResponse(token, email, expires);
    }

    public Optional<String> authenticate(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) return Optional.empty();
        return jdbc.queryForList("SELECT s.admin_email FROM challenge_platform_admin.admin_session s "
                + "JOIN challenge_platform_admin.admin_user u ON u.email = s.admin_email "
                + "WHERE s.token_hash = ? AND s.expires_at > now() AND u.enabled = true",
                String.class, hash(token)).stream().findFirst();
    }

    public void revoke(String token) {
        jdbc.update("DELETE FROM challenge_platform_admin.admin_session WHERE token_hash = ?", hash(token));
    }

    private String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
