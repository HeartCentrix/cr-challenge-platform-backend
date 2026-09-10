package com.qfion.challenge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qfion.challenge.dto.AdminStatsDto.AttemptDetail;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.tools.ToolProvider;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AiSourceMarkerTest {
    @TempDir Path temp;
    private static final String MARKER = "/*" + String.valueOf((char) 173).repeat(64) + "*/";

    @Test void detectsTheExactMarkerInSavedJavaComments() {
        assertTrue(AiSourceMarker.detected("public class Main { " + MARKER + "\n}"));
        assertTrue(AiSourceMarker.detected(MARKER + "\r\nclass Main {}"));
    }

    @Test void ordinaryCodeUnrelatedUnicodeAndPartialMarkersDoNotMatch() {
        assertFalse(AiSourceMarker.detected(null));
        assertFalse(AiSourceMarker.detected(""));
        assertFalse(AiSourceMarker.detected("class Main { /* normal comment */ }"));
        assertFalse(AiSourceMarker.detected("// فارسی 日本語 \u200b\u200c\u2063"));
        assertFalse(AiSourceMarker.detected(MARKER.substring(0, MARKER.length() - 1)));
        assertFalse(AiSourceMarker.detected(MARKER.substring(0, 10) + " " + MARKER.substring(10)));
        assertFalse(AiSourceMarker.detected(MARKER.replace('\u00ad', ' ')));
        assertFalse(AiSourceMarker.detected("/*" + "\u00ad".repeat(63) + "*/"));
        assertFalse(AiSourceMarker.detected("/*" + "\u00ad".repeat(65) + "*/"));
        assertFalse(AiSourceMarker.detected("// ordinary soft\u00adhyphen"));
    }

    @Test void markerInsideACommentDoesNotBreakJavaCompilation() throws Exception {
        Path source = temp.resolve("Main.java");
        Files.writeString(source, "public class Main { " + MARKER + " public static void main(String[] args) { System.out.println(1 + 2); } }");
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-encoding", "UTF-8", "-d", temp.toString(), source.toString()));
    }

    @Test void adminResponseDerivesFlagFromSourceAndPreservesInvisibleCharacters() throws Exception {
        var mapper = new ObjectMapper();
        for (var source : List.of("class Main {}", "class Main { " + MARKER + " }")) {
            var detail = new AttemptDetail(null, source, "Prompt", 8, 600, "", "", null, null, List.of(), null, null, null);
            var json = mapper.readTree(mapper.writeValueAsString(detail));
            assertEquals(source, json.get("sourceCode").asText());
            assertEquals(source.contains(MARKER), json.get("aiMarkerDetected").asBoolean());
        }
    }
}
