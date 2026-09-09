package com.qfion.challenge.controller;

import com.qfion.challenge.service.ActivityCheckpointService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ActivityCheckpointTest {
    @Test void acceptsBoundedWriteOnlyCheckpointsAndRejectsOversizedCode() throws Exception {
        var service = mock(ActivityCheckpointService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new ActivityCheckpointController(service)).setControllerAdvice(new ActivityCheckpointErrors()).build();
        String body = "{\"token\":\"4a394bdd-931f-4054-bc51-cb6b299baad9\",\"sequence\":1,\"slug\":\"test-question\",\"sourceCode\":\"code\",\"activity\":" + EditorActivityTest.ACTIVITY + "}";
        mvc.perform(post("/api/v1/activity-checkpoints").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        mvc.perform(get("/api/v1/activity-checkpoints")).andExpect(status().isMethodNotAllowed());
        mvc.perform(post("/api/v1/activity-checkpoints").contentType(MediaType.APPLICATION_JSON).content(body.replace("\"sequence\":1", "\"sequence\":121"))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/activity-checkpoints").contentType(MediaType.APPLICATION_JSON).content(body.replace("\"code\"", "\"" + "x".repeat(32001) + "\""))).andExpect(status().isBadRequest());
        verify(service, times(1)).save(any());
    }
}
