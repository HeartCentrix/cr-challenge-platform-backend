package com.qfion.challenge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * Thin wrapper over Judge0's synchronous, base64-encoded submission endpoint.
 *
 * Uses the JDK HTTP client with an explicitly serialised JSON body. RestTemplate was tried
 * first and Judge0 intermittently rejected the request with
 * {@code {"source_code":["can't be blank"]}} - the body was not arriving intact.
 */
@Slf4j
@Service
public class Judge0Client {

    /** Judge0 status id 3 = Accepted. Anything else is a failure of some kind. */
    public static final int STATUS_ACCEPTED = 3;

    private final ObjectMapper mapper = new ObjectMapper();
    private final String submitUrl;
    private final int timeoutSeconds;
    private final double cpuTimeLimitSeconds;
    private HttpClient http;

    public Judge0Client(@Value("${challenge.judge.base-url}") String baseUrl,
                        @Value("${challenge.judge.timeout-seconds:120}") int timeoutSeconds,
                        @Value("${challenge.judge.cpu-time-limit-seconds:2.0}") double cpuTimeLimitSeconds) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.submitUrl = base + "/submissions/?base64_encoded=true&wait=true";
        this.timeoutSeconds = timeoutSeconds;
        this.cpuTimeLimitSeconds = cpuTimeLimitSeconds;
    }

    @PostConstruct
    void init() {
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public record Execution(int statusId, String statusDescription, String stdout, String stderr,
                            Integer execTimeMs, Integer memoryKb) {
        public boolean accepted() { return statusId == STATUS_ACCEPTED; }
    }

    /**
     * Runs one submission against one input. When {@code expectedOutput} is supplied Judge0
     * performs the comparison itself and returns status 3 only on an exact match.
     */
    public Execution execute(String sourceCode, int languageId, String stdin, String expectedOutput) {
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("source_code", b64(sourceCode));
            body.put("language_id", languageId);
            body.put("stdin", b64(stdin == null ? "" : stdin));
            if (expectedOutput != null) {
                body.put("expected_output", b64(expectedOutput));
            }
            // Without an explicit limit Judge0 allows several seconds, which lets an O(n^2)
            // solution pass the large test cases. This is what makes "not optimal" fail.
            body.put("cpu_time_limit", cpuTimeLimitSeconds);
            body.put("wall_time_limit", cpuTimeLimitSeconds * 3);
            String json = mapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder(URI.create(submitUrl))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                log.error("Judge0 returned HTTP {}: {}", response.statusCode(), truncate(response.body()));
                return new Execution(-1, "judge error " + response.statusCode(), null, response.body(), null, null);
            }

            JsonNode res = mapper.readTree(response.body());
            JsonNode status = res.path("status");
            int statusId = status.path("id").asInt(-1);
            String statusDesc = status.path("description").asText("unknown");

            Integer timeMs = null;
            if (res.hasNonNull("time")) {
                try {
                    timeMs = (int) Math.round(Double.parseDouble(res.get("time").asText()) * 1000);
                } catch (NumberFormatException ignored) {
                    // Judge0 occasionally omits timing; not worth failing the submission over.
                }
            }
            Integer memory = res.hasNonNull("memory") ? res.get("memory").asInt() : null;

            return new Execution(statusId, statusDesc,
                    decode(res.path("stdout")), decode(res.path("stderr")), timeMs, memory);

        } catch (Exception e) {
            log.error("Judge0 call failed: {}", e.toString());
            return new Execution(-1, "judge unavailable", null, e.getMessage(), null, null);
        }
    }

    private static String b64(String s) {
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) return null;
        try {
            // MIME decoder, not the basic one: Judge0 sometimes appends a newline to the base64.
            return new String(Base64.getMimeDecoder().decode(node.asText()), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return node.asText();
        }
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() <= 300 ? s : s.substring(0, 300) + "...";
    }
}
