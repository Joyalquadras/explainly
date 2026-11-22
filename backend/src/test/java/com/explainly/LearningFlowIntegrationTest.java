package com.explainly;

import com.explainly.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LearningFlowIntegrationTest extends IntegrationTest {

    private static final String TRANSCRIPT = """
            0:00
            Welcome. In this video we look at how a Spring Boot application handles a web request from start to finish.
            0:05
            The controller is the first piece of your code that receives the HTTP request. It maps URLs to methods.
            0:30
            The controller then calls a service, where the business logic lives. The service uses a repository.
            0:45
            The repository talks to the database using SQL, and the result travels back up to the client as JSON.
            """;

    @Test
    void fullLearningJourney() throws Exception {
        String token = guestToken();

        // 1. Submit -> no automatic transcript available -> AWAITING_TRANSCRIPT with a clear explanation.
        JsonNode submitted = json(mvc.perform(authed(post("/api/videos/analyze"), token)
                        .content(body(Map.of("url", "https://youtu.be/dQw4w9WgXcQ"))))
                .andExpect(status().isAccepted()).andReturn());
        long id = submitted.path("analysis").path("id").asLong();
        assertThat(submitted.path("video").path("title").asText()).isEqualTo("Spring Boot in 100 seconds");
        JsonNode awaiting = waitFor(token, id, "AWAITING_TRANSCRIPT");
        assertThat(awaiting.path("canSubmitTranscript").asBoolean()).isTrue();
        assertThat(awaiting.path("statusMessage").asText()).contains("paste the transcript");

        // Submitting the same URL again returns the same analysis.
        JsonNode again = json(mvc.perform(authed(post("/api/videos/analyze"), token)
                .content(body(Map.of("url", "https://www.youtube.com/watch?v=dQw4w9WgXcQ")))).andExpect(status().isOk()).andReturn());
        assertThat(again.path("analysis").path("id").asLong()).isEqualTo(id);
        assertThat(again.path("reused").asBoolean()).isTrue();

        // Learning features are not available before processing completes.
        mvc.perform(authed(get("/api/analyses/" + id + "/notes"), token)).andExpect(status().isConflict());

        // 2. Upload a transcript file -> pipeline completes.
        mvc.perform(multipart("/api/analyses/" + id + "/transcript/upload")
                        .file(new MockMultipartFile("file", "captions.txt", "text/plain", TRANSCRIPT.getBytes()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"));
        waitFor(token, id, "COMPLETED");

        // 3. Full detail: overview, chapters with timestamps, concepts, illustrations rendered as SVG.
        JsonNode detail = json(mvc.perform(authed(get("/api/analyses/" + id), token)).andExpect(status().isOk()).andReturn());
        assertThat(detail.path("status").path("category").asText()).isEqualTo("Programming");
        assertThat(detail.path("overview").path("title").asText()).isEqualTo("How Spring Boot handles a request");
        assertThat(detail.path("transcriptTimed").asBoolean()).isTrue();
        JsonNode chapters = detail.path("chapters");
        assertThat(chapters).hasSize(2);
        assertThat(chapters.get(1).path("startSeconds").asInt()).isEqualTo(30);
        assertThat(chapters.get(0).path("endSeconds").asInt()).isEqualTo(30);
        assertThat(detail.path("concepts").get(1).path("kind").asText()).isEqualTo("CONCEPT");
        assertThat(detail.path("summaries").path("oneMinute").asText()).isEqualTo("Short summary.");
        JsonNode illustrations = detail.path("illustrations");
        assertThat(illustrations).hasSize(2); // invalid comparison dropped
        assertThat(illustrations.get(0).path("svg").asText()).startsWith("<svg").contains("Database &lt;script&gt;").doesNotContain("<script");
        assertThat(illustrations.get(0).path("chapterId").asLong()).isEqualTo(chapters.get(0).path("id").asLong());
        long illustrationId = illustrations.get(0).path("id").asLong();
        mvc.perform(get("/api/illustrations/" + illustrationId + "/svg").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(content().contentType("image/svg+xml"));

        // 4. Chat grounded in the transcript, with timestamp citations.
        JsonNode reply = json(mvc.perform(authed(post("/api/analyses/" + id + "/chat"), token)
                .content(body(Map.of("message", "What does the controller do?")))).andExpect(status().isOk()).andReturn());
        assertThat(reply.path("role").asText()).isEqualTo("assistant");
        assertThat(reply.path("citations").toString()).isEqualTo("[5,30]");
        mvc.perform(authed(get("/api/analyses/" + id + "/chat"), token)).andExpect(jsonPath("$.length()").value(2));

        // 5. Notes: generated by default, editable, exportable as PDF, resettable.
        mvc.perform(authed(get("/api/analyses/" + id + "/notes"), token))
                .andExpect(jsonPath("$.edited").value(false)).andExpect(jsonPath("$.content").value(org.hamcrest.Matchers.startsWith("# Notes")));
        mvc.perform(authed(put("/api/analyses/" + id + "/notes"), token).content(body(Map.of("content", "# My notes\n- **edited**"))))
                .andExpect(jsonPath("$.edited").value(true));
        byte[] pdf = mvc.perform(get("/api/analyses/" + id + "/notes/pdf").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(".pdf")))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");

        // 6. Quiz: answers hidden until graded; score tracked.
        mvc.perform(authed(get("/api/analyses/" + id + "/quiz"), token)).andExpect(status().isNoContent());
        JsonNode quiz = json(mvc.perform(authed(post("/api/analyses/" + id + "/quiz"), token)).andExpect(status().isOk()).andReturn());
        assertThat(quiz.path("questions")).hasSize(3);
        assertThat(quiz.path("questions").get(0).has("correctIndex")).isFalse();
        assertThat(quiz.path("questions").get(2).path("codeSnippet").asText()).contains("/orders");
        long quizId = quiz.path("id").asLong();
        mvc.perform(authed(post("/api/quizzes/" + quizId + "/attempts"), token).content(body(Map.of("answers", List.of(0)))))
                .andExpect(status().isBadRequest());
        JsonNode graded = json(mvc.perform(authed(post("/api/quizzes/" + quizId + "/attempts"), token)
                .content(body(Map.of("answers", List.of(0, 0, 0))))).andExpect(status().isOk()).andReturn());
        assertThat(graded.path("attempt").path("score").asInt()).isEqualTo(2);
        assertThat(graded.path("results").get(1).path("correct").asBoolean()).isFalse();
        assertThat(graded.path("results").get(1).path("correctIndex").asInt()).isEqualTo(1);

        // 7. Flashcards.
        mvc.perform(authed(post("/api/analyses/" + id + "/flashcards"), token)).andExpect(jsonPath("$.length()").value(3));
        mvc.perform(authed(get("/api/analyses/" + id + "/flashcards"), token)).andExpect(jsonPath("$[0].definition").value("Entry point"));

        // 8. Bookmarks and progress.
        long chapterId = chapters.get(1).path("id").asLong();
        mvc.perform(authed(post("/api/bookmarks"), token).content(body(Map.of("analysisId", id, "chapterId", chapterId,
                "timestampSeconds", 30, "title", "Services")))).andExpect(status().isCreated());
        mvc.perform(authed(get("/api/bookmarks?analysisId=" + id), token)).andExpect(jsonPath("$.items[0].title").value("Services"));
        mvc.perform(authed(put("/api/history/" + id + "/progress"), token)
                        .content(body(Map.of("completedChapterIds", List.of(chapterId, 999999), "lastPositionSeconds", 42))))
                .andExpect(jsonPath("$.progressPercent").value(50)).andExpect(jsonPath("$.completedChapterIds.length()").value(1));
        mvc.perform(authed(get("/api/history?category=Programming"), token)).andExpect(jsonPath("$.items[0].progressPercent").value(50));
        mvc.perform(authed(get("/api/history/stats"), token))
                .andExpect(jsonPath("$.videos").value(1)).andExpect(jsonPath("$.quizAttempts").value(1))
                .andExpect(jsonPath("$.averageQuizScore").value(67)).andExpect(jsonPath("$.categories.Programming").value(1));

        // 9. Another user cannot see this analysis or its data.
        String other = guestToken();
        mvc.perform(authed(get("/api/analyses/" + id), other)).andExpect(status().isNotFound());
        mvc.perform(authed(post("/api/analyses/" + id + "/chat"), other).content(body(Map.of("message", "hi")))).andExpect(status().isNotFound());
        mvc.perform(get("/api/illustrations/" + illustrationId + "/svg").header("Authorization", "Bearer " + other)).andExpect(status().isNotFound());
        mvc.perform(authed(post("/api/quizzes/" + quizId + "/attempts"), other).content(body(Map.of("answers", List.of(0, 0, 0)))))
                .andExpect(status().isNotFound());
    }

    @Test
    void identicalTranscriptIsNotReprocessed() throws Exception {
        String a = guestToken();
        String b = guestToken();
        long first = submitWithPastedTranscript(a, "https://www.youtube.com/watch?v=abcdefghijk");
        waitFor(a, first, "COMPLETED");
        long second = submitWithPastedTranscript(b, "https://www.youtube.com/watch?v=abcdefghijk");
        JsonNode status = waitFor(b, second, "COMPLETED");
        assertThat(second).isNotEqualTo(first);
        assertThat(status.path("statusMessage").asText()).contains("reused");
        mvc.perform(authed(get("/api/analyses/" + second), b)).andExpect(jsonPath("$.chapters.length()").value(2));
    }

    @Test
    void rejectsInvalidInput() throws Exception {
        String token = guestToken();
        mvc.perform(authed(post("/api/videos/analyze"), token).content(body(Map.of("url", "http://169.254.169.254/latest/meta-data"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_YOUTUBE_URL"));
        JsonNode submitted = json(mvc.perform(authed(post("/api/videos/analyze"), token)
                .content(body(Map.of("url", "https://youtu.be/zzzzzzzzzzz")))).andReturn());
        long id = submitted.path("analysis").path("id").asLong();
        waitFor(token, id, "AWAITING_TRANSCRIPT");
        mvc.perform(multipart("/api/analyses/" + id + "/transcript/upload")
                        .file(new MockMultipartFile("file", "evil.exe", "application/octet-stream", new byte[]{1, 2, 3}))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNSUPPORTED_FILE_TYPE"));
        mvc.perform(multipart("/api/analyses/" + id + "/transcript/upload")
                        .file(new MockMultipartFile("file", "bin.txt", "text/plain", new byte[]{(byte) 0xff, (byte) 0xfe, 0, 1}))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_TEXT_FILE"));
        mvc.perform(authed(post("/api/analyses/" + id + "/transcript"), token).content(body(Map.of("text", "too short"))))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("TRANSCRIPT_TOO_SHORT"));
        mvc.perform(multipart("/api/analyses/" + id + "/transcript/audio")
                        .file(new MockMultipartFile("file", "a.mp3", "audio/mpeg", new byte[]{1}))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isServiceUnavailable());
    }

    private long submitWithPastedTranscript(String token, String url) throws Exception {
        long id = json(mvc.perform(authed(post("/api/videos/analyze"), token).content(body(Map.of("url", url)))).andReturn())
                .path("analysis").path("id").asLong();
        waitFor(token, id, "AWAITING_TRANSCRIPT");
        mvc.perform(authed(post("/api/analyses/" + id + "/transcript"), token).content(body(Map.of("text", TRANSCRIPT))))
                .andExpect(status().isOk());
        return id;
    }

    private JsonNode waitFor(String token, long id, String expected) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        JsonNode status = null;
        while (System.nanoTime() < deadline) {
            status = json(mvc.perform(authed(get("/api/analyses/" + id + "/status"), token).contentType(MediaType.APPLICATION_JSON)).andReturn());
            if (expected.equals(status.path("status").asText())) return status;
            if ("FAILED".equals(status.path("status").asText())) break;
            Thread.sleep(50);
        }
        throw new AssertionError("Expected " + expected + " but was " + status);
    }
}
