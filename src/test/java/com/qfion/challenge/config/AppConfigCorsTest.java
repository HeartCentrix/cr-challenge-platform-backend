package com.qfion.challenge.config;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.DefaultCorsProcessor;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import static org.junit.jupiter.api.Assertions.*;

class AppConfigCorsTest {
    private static class Registry extends CorsRegistry {
        CorsConfiguration api() { return getCorsConfigurations().get("/api/**"); }
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://challenge.codereport.com", "https://challenge.dev.codereport.com", "http://localhost:4200"})
    void allowsCandidatePostsAndAdminPreflights(String origin) throws Exception {
        var app = new AppConfig();
        var registry = new Registry();
        app.addCorsMappings(registry);
        var post = new MockHttpServletRequest("POST", "/api/v1/submit");
        post.addHeader("Origin", origin);
        var response = new MockHttpServletResponse();
        assertTrue(new DefaultCorsProcessor().processRequest(registry.api(), post, response));
        assertEquals(origin, response.getHeader("Access-Control-Allow-Origin"));

        var preflight = new MockHttpServletRequest("OPTIONS", "/api/v1/admin/login");
        preflight.addHeader("Origin", origin);
        preflight.addHeader("Access-Control-Request-Method", "POST");
        preflight.addHeader("Access-Control-Request-Headers", "authorization,content-type");
        var adminResponse = new MockHttpServletResponse();
        app.adminCorsFilter().getFilter().doFilter(preflight, adminResponse, new MockFilterChain());
        assertEquals(200, adminResponse.getStatus());
        assertEquals(origin, adminResponse.getHeader("Access-Control-Allow-Origin"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://untrusted.example", "https://challenge.codereport.com.untrusted.example"})
    void rejectsUnrelatedOrigins(String origin) throws Exception {
        var app = new AppConfig();
        var registry = new Registry();
        app.addCorsMappings(registry);
        var request = new MockHttpServletRequest("POST", "/api/v1/submit");
        request.addHeader("Origin", origin);
        var response = new MockHttpServletResponse();
        assertFalse(new DefaultCorsProcessor().processRequest(registry.api(), request, response));
        assertEquals(403, response.getStatus());
        var adminRequest = new MockHttpServletRequest("POST", "/api/v1/admin/login");
        adminRequest.addHeader("Origin", origin);
        var adminResponse = new MockHttpServletResponse();
        app.adminCorsFilter().getFilter().doFilter(adminRequest, adminResponse, new MockFilterChain());
        assertEquals(403, adminResponse.getStatus());
    }
}
