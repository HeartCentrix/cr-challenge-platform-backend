package com.qfion.challenge.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qfion.challenge.dto.Dto;
import com.qfion.challenge.service.EditorActivityCodec;
import com.qfion.challenge.service.SubmissionService;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class EditorActivityTest {
    static final String ACTIVITY = """
        {"version":1,"questionSlug":"test-question","startedAt":"2026-09-09T10:00:00Z","elapsedMs":1000,
         "question":{"copy":1,"cut":0,"paste":0,"drop":0},"answer":{"copy":0,"cut":0,"paste":1,"drop":0},
         "keydownCount":1,"trustedKeydownCount":1,"syntheticEvents":0,"modelChangeCount":1,
         "unexplainedChangeCount":0,"observedPasteCount":0,"insertedCharacters":1,"deletedCharacters":0,"droppedEvents":0,
         "events":[{"offsetMs":10,"kind":"key-character","area":"answer","trusted":true}]}
        """;
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test void roundTripsTypedDataAndRejectsCrossQuestionOrImpossibleTiming() throws Exception {
        var report = JSON.readValue(ACTIVITY, Dto.EditorActivity.class);
        assertEquals(report, EditorActivityCodec.decode(EditorActivityCodec.encode(report, "test-question")));
        assertNull(EditorActivityCodec.encode(null, "old-client"));
        assertNull(EditorActivityCodec.decode(null));
        assertThrows(ResponseStatusException.class, () -> EditorActivityCodec.encode(report, "different-question"));
        for (String bad : new String[] { ACTIVITY.replace("\"offsetMs\":10", "\"offsetMs\":1001"),
                ACTIVITY.replace("\"trustedKeydownCount\":1", "\"trustedKeydownCount\":2"),
                ACTIVITY.replace("2026-09-09T10:00:00Z", "not-a-date"),
                ACTIVITY.replace("\"area\":\"answer\"", "\"area\":\"question\"") }) {
            assertThrows(ResponseStatusException.class, () -> EditorActivityCodec.encode(JSON.readValue(bad, Dto.EditorActivity.class), "test-question"));
        }
    }

    @Test void validatesNestedPayloadLimitsAndEventKinds() throws Exception {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertTrue(validator.validate(JSON.readValue(ACTIVITY, Dto.EditorActivity.class)).isEmpty());
            for (String bad : new String[] { ACTIVITY.replace("\"copy\":1", "\"copy\":-1"),
                    ACTIVITY.replace("key-character", "literal-password"), ACTIVITY.replace("\"version\":1", "\"version\":2") }) {
                assertFalse(validator.validate(JSON.readValue(bad, Dto.EditorActivity.class)).isEmpty());
            }
            var large = (com.fasterxml.jackson.databind.node.ObjectNode) JSON.readTree(ACTIVITY);
            var event = large.withArray("events").get(0).deepCopy();
            for (int i = 0; i < 5000; i++) large.withArray("events").add(event);
            assertFalse(validator.validate(JSON.treeToValue(large, Dto.EditorActivity.class)).isEmpty());
        }
    }

    @Test void acceptsTelemetryWithoutExposingItInPublicAcknowledgement() throws Exception {
        var service = mock(SubmissionService.class);
        when(service.submit(any(), any(), nullable(String.class))).thenReturn(new Dto.SubmitResponse("Saved"));
        String request = """
            {"slug":"test-question","sourceCode":"class Main {}","fullName":"Test Candidate",
             "email":"test@example.invalid","phone":"2025550196","consent":false,"editorActivity":
            """ + ACTIVITY + "}";
        MockMvcBuilders.standaloneSetup(new SubmissionController(service)).build()
                .perform(post("/api/v1/submit").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk()).andExpect(content().json("{\"message\":\"Saved\"}", true));
        verify(service).submit(argThat(req -> req.editorActivity().question().copy() == 1), any(), nullable(String.class));
    }
}
