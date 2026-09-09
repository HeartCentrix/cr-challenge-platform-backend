package com.qfion.challenge.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qfion.challenge.dto.Dto.EditorActivity;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

/** Bounded, typed client observations. Never execute or trust their contents. */
public final class EditorActivityCodec {
    private static final ObjectMapper JSON = new ObjectMapper();
    private EditorActivityCodec() {}

    public static String encode(EditorActivity activity, String slug) {
        if (activity == null) return null;
        if (!slug.equals(activity.questionSlug()) || activity.trustedKeydownCount() > activity.keydownCount()) throw invalid();
        if (activity.unexplainedBulkChangeCount() != null && (activity.bulkChangeCount() == null
                || activity.unexplainedBulkChangeCount() > activity.bulkChangeCount())) throw invalid();
        if (activity.largestInsertion() != null && activity.largestInsertion() > activity.insertedCharacters()) throw invalid();
        try { OffsetDateTime.parse(activity.startedAt()); }
        catch (DateTimeParseException error) { throw invalid(); }
        long previous = -1;
        for (var event : activity.events()) {
            if (event.offsetMs() < previous || event.offsetMs() > activity.elapsedMs()) throw invalid();
            if (event.kind().startsWith("key-") && !"answer".equals(event.area())) throw invalid();
            previous = event.offsetMs();
        }
        try {
            var value = JSON.writeValueAsString(activity);
            if (value.length() > 1000000) throw invalid();
            return value;
        } catch (JsonProcessingException error) { throw invalid(); }
    }

    public static EditorActivity decode(String value) {
        if (value == null) return null;
        try { return JSON.readValue(value, EditorActivity.class); }
        catch (JsonProcessingException error) { throw new IllegalStateException("Invalid stored editor activity", error); }
    }

    private static ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid editor activity for this question.");
    }
}
