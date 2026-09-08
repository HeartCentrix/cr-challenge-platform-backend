package com.qfion.challenge.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import com.qfion.challenge.service.AdminSessionService;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;

/** Explicit per-request credentials; never trust the admin page URL or browser state. */
@Component
public class AdminCredentialsFilter extends OncePerRequestFilter {
    private final JdbcTemplate jdbc;
    private final AdminSessionService sessions;
    // Keep unknown-user authentication comparable in cost to a password check.
    private final String dummyHash = BCrypt.hashpw("unused-admin-password", BCrypt.gensalt(12));

    public AdminCredentialsFilter(JdbcTemplate jdbc, AdminSessionService sessions) {
        this.jdbc = jdbc;
        this.sessions = sessions;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        if (path.isEmpty()) path = request.getRequestURI();
        path = path.replaceAll(";[^/]*", "").replaceAll("/{2,}", "/");
        return !(path.equals("/api/v1/admin") || path.startsWith("/api/v1/admin/"))
                || request.getMethod().equals("OPTIONS");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                  FilterChain chain) throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-store");
        String authorization = request.getHeader("Authorization");
        String path = request.getServletPath();
        if (path.isEmpty()) path = request.getRequestURI();
        path = path.replaceAll(";[^/]*", "").replaceAll("/{2,}", "/");
        boolean login = path.equals("/api/v1/admin/auth/login") && request.getMethod().equals("POST");
        if (!login) {
            String token = authorization != null && authorization.startsWith("Bearer ")
                    ? authorization.substring(7) : null;
            try {
                var email = sessions.authenticate(token);
                if (email.isEmpty()) { reject(response, 401, "Please sign in to continue."); return; }
                request.setAttribute("authenticatedAdmin", email.get());
                request.setAttribute("adminSessionToken", token);
            } catch (org.springframework.dao.DataAccessException e) {
                reject(response, 503, "Admin access is unavailable on this server."); return;
            }
            chain.doFilter(request, response);
            return;
        }
        if (authorization == null || !authorization.startsWith("Basic ") || authorization.length() > 2048) {
            reject(response, 401, "Valid admin credentials are required.");
            return;
        }
        String decoded;
        try {
            decoded = new String(Base64.getDecoder().decode(authorization.substring(6)), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            reject(response, 401, "Valid admin credentials are required.");
            return;
        }
        int separator = decoded.indexOf(':');
        if (separator <= 0) { reject(response, 401, "Valid admin credentials are required."); return; }
        String email = decoded.substring(0, separator).trim().toLowerCase(Locale.ROOT);
        String password = decoded.substring(separator + 1);
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            reject(response, 401, "Valid admin credentials are required."); return;
        }
        try {
            var hashes = jdbc.queryForList("SELECT password_hash FROM challenge_platform_admin.admin_user "
                    + "WHERE email = ? AND enabled = true", String.class, email);
            boolean matches = BCrypt.checkpw(password, hashes.isEmpty() ? dummyHash : hashes.get(0));
            if (hashes.isEmpty() || !matches) {
                reject(response, 401, "Valid admin credentials are required."); return;
            }
        } catch (org.springframework.dao.DataAccessException e) {
            // Fail closed when the admin migration has not been installed.
            reject(response, 503, "Admin access is unavailable on this server."); return;
        }
        request.setAttribute("authenticatedAdmin", email);
        chain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        // Do not send WWW-Authenticate: browsers must not cache credentials or send them implicitly.
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
