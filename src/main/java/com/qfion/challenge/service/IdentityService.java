package com.qfion.challenge.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Turns raw contact details into a stable identity.
 *
 * Normalisation matters as much as the database constraint: without stripping dots
 * and plus-tags, alice+1@gmail.com and a.lice@gmail.com defeat the daily limit.
 */
@Service
public class IdentityService {

    private final String salt;

    public IdentityService(@Value("${challenge.identity-salt}") String salt) {
        this.salt = salt;
    }

    public String normaliseEmail(String raw) {
        if (raw == null) return null;
        String e = raw.trim().toLowerCase();
        int at = e.indexOf('@');
        if (at <= 0) return e;
        String local = e.substring(0, at);
        String domain = e.substring(at + 1);
        int plus = local.indexOf('+');
        if (plus > 0) local = local.substring(0, plus);
        if (domain.equals("gmail.com") || domain.equals("googlemail.com")) {
            local = local.replace(".", "");
            domain = "gmail.com";
        }
        return local + "@" + domain;
    }

    /** Keeps digits only, then trims to the last 10 so +1-555-0100 and 5550100 match. */
    public String normalisePhone(String raw) {
        if (raw == null) return null;
        String digits = raw.replaceAll("[^0-9]", "");
        return digits.length() > 10 ? digits.substring(digits.length() - 10) : digits;
    }

    public String hash(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] out = md.digest((salt + "|" + value).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(out.length * 2);
            for (byte b : out) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
