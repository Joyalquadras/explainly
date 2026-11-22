package com.explainly.explanation;

import com.explainly.ai.AiService;
import com.explainly.ai.LlmRequest;
import com.explainly.ai.Prompts;
import com.explainly.ai.TranscriptPreparer;
import com.explainly.common.Json;
import com.explainly.common.TimeFormat;
import com.explainly.video.Video;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.*;

/** Calls the model for the structured explanation and the learning modes, and validates the output. */
@Component
public class ExplanationGenerator {

    private static final Set<String> CATEGORIES = new HashSet<>(Arrays.asList(Prompts.CATEGORIES.split(",\\s*")));
    private static final Set<String> CONCEPT_KINDS = Set.of("DEFINITION", "TERMINOLOGY", "FORMULA", "CONCEPT");
    private final AiService ai;

    public ExplanationGenerator(AiService ai) {
        this.ai = ai;
    }

    public record ChapterDraft(String title, Integer startSeconds, Integer endSeconds, String shortSummary,
                               String simpleExplanation, String detailedExplanation, String realWorldExample,
                               String practicalUseCase, List<String> keyPoints, List<String> concepts) {}

    public record Structure(String category, ObjectNode overview, List<ChapterDraft> chapters, ArrayNode concepts) {}

    public record LearningModes(String beginner, String technical, String oneMinute, String fiveMinute,
                                String detailed, String notes) {}

    public Structure generateStructure(Video video, TranscriptPreparer.Prepared transcript) {
        JsonNode json = ai.completeJson(LlmRequest.json("structure", Prompts.TEACHER,
                Prompts.structure(video.getTitle(), video.getChannelTitle(), video.getDurationSeconds(),
                        transcript.text(), transcript.timed()), 24000));

        String category = json.path("category").asText("Other");
        if (!CATEGORIES.contains(category)) category = "Other";

        ObjectNode overview = Json.MAPPER.createObjectNode();
        JsonNode o = json.path("overview");
        overview.put("title", text(o, "title", video.getTitle(), 200));
        overview.put("whatIsItAbout", text(o, "whatIsItAbout", "", 2000));
        overview.set("whatYouWillLearn", strings(o.path("whatYouWillLearn"), 10, 300));
        overview.put("whyItMatters", text(o, "whyItMatters", "", 2000));
        overview.put("whoShouldWatch", text(o, "whoShouldWatch", "", 1000));
        String difficulty = o.path("difficulty").asText("Beginner");
        overview.put("difficulty", List.of("Beginner", "Intermediate", "Advanced").contains(difficulty) ? difficulty : "Beginner");
        overview.set("keyTakeaways", strings(o.path("keyTakeaways"), 10, 300));
        overview.put("language", text(json, "language", "en", 10));

        Integer duration = video.getDurationSeconds();
        List<ChapterDraft> chapters = new ArrayList<>();
        for (JsonNode c : json.path("chapters")) {
            if (chapters.size() >= 20) break;
            String title = text(c, "title", "", 200);
            if (title.isBlank()) continue;
            Integer start = transcript.timed() ? seconds(c.path("startSeconds"), duration) : null;
            Integer end = transcript.timed() ? seconds(c.path("endSeconds"), duration) : null;
            chapters.add(new ChapterDraft(title, start, end,
                    text(c, "shortSummary", "", 600), text(c, "simpleExplanation", "", 4000),
                    text(c, "detailedExplanation", "", 8000), text(c, "realWorldExample", "", 3000),
                    text(c, "practicalUseCase", "", 3000), list(c.path("keyPoints"), 8, 400), list(c.path("concepts"), 10, 120)));
        }
        if (chapters.isEmpty()) throw new IllegalStateException("The AI did not return any chapters.");
        if (transcript.timed()) {
            chapters.sort(Comparator.comparing(ch -> ch.startSeconds() == null ? Integer.MAX_VALUE : ch.startSeconds()));
            // Fill missing end times from the next chapter's start.
            for (int i = 0; i < chapters.size(); i++) {
                ChapterDraft ch = chapters.get(i);
                if (ch.endSeconds() == null && i + 1 < chapters.size() && chapters.get(i + 1).startSeconds() != null) {
                    chapters.set(i, new ChapterDraft(ch.title(), ch.startSeconds(), chapters.get(i + 1).startSeconds(), ch.shortSummary(),
                            ch.simpleExplanation(), ch.detailedExplanation(), ch.realWorldExample(), ch.practicalUseCase(),
                            ch.keyPoints(), ch.concepts()));
                }
            }
        }

        ArrayNode concepts = Json.MAPPER.createArrayNode();
        for (JsonNode c : json.path("concepts")) {
            if (concepts.size() >= 40) break;
            String term = text(c, "term", "", 150);
            if (term.isBlank()) continue;
            ObjectNode n = concepts.addObject();
            n.put("term", term);
            String kind = c.path("kind").asText("CONCEPT").toUpperCase(Locale.ROOT);
            n.put("kind", CONCEPT_KINDS.contains(kind) ? kind : "CONCEPT");
            n.put("definition", text(c, "definition", "", 1500));
            n.put("simpleExplanation", text(c, "simpleExplanation", "", 1500));
            Integer ts = transcript.timed() ? seconds(c.path("timestampSeconds"), duration) : null;
            if (ts == null) n.putNull("timestampSeconds"); else n.put("timestampSeconds", ts);
        }
        return new Structure(category, overview, chapters, concepts);
    }

    public LearningModes generateModes(Video video, TranscriptPreparer.Prepared transcript, List<ChapterDraft> chapters) {
        StringBuilder outline = new StringBuilder();
        for (int i = 0; i < chapters.size(); i++) {
            ChapterDraft c = chapters.get(i);
            outline.append(i + 1).append(". ");
            if (c.startSeconds() != null) outline.append('[').append(TimeFormat.clock(c.startSeconds())).append("] ");
            outline.append(c.title()).append(" — ").append(c.shortSummary()).append('\n');
        }
        JsonNode json = ai.completeJson(LlmRequest.json("learning-modes", Prompts.TEACHER,
                Prompts.learningModes(video.getTitle(), outline.toString(), transcript.text()), 24000));
        JsonNode s = json.path("summaries");
        return new LearningModes(text(json, "beginnerExplanation", "", 20000), text(json, "technicalExplanation", "", 20000),
                text(s, "oneMinute", "", 4000), text(s, "fiveMinute", "", 12000), text(s, "detailed", "", 30000),
                text(json, "notes", "", 30000));
    }

    static Integer seconds(JsonNode n, Integer duration) {
        if (n == null || n.isNull() || n.isMissingNode()) return null;
        Integer value = null;
        if (n.isNumber()) value = (int) Math.round(n.asDouble());
        else if (n.isTextual()) {
            value = TimeFormat.parseClock(n.asText());
            if (value == null) {
                try { value = (int) Math.round(Double.parseDouble(n.asText())); } catch (NumberFormatException ignored) { /* invalid */ }
            }
        }
        if (value == null || value < 0) return null;
        if (duration != null && duration > 0 && value > duration) return duration;
        return value;
    }

    static String text(JsonNode n, String field, String fallback, int max) {
        JsonNode v = n.path(field);
        String s = v.isTextual() ? v.asText().trim() : (v.isMissingNode() || v.isNull() ? fallback : v.toString());
        if (s == null || s.isBlank()) s = fallback;
        return s.length() <= max ? s : s.substring(0, max);
    }

    static List<String> list(JsonNode arr, int maxItems, int maxLen) {
        List<String> out = new ArrayList<>();
        for (JsonNode n : arr) {
            if (out.size() >= maxItems) break;
            String s = n.asText("").trim();
            if (!s.isEmpty()) out.add(s.length() <= maxLen ? s : s.substring(0, maxLen));
        }
        return out;
    }

    private static ArrayNode strings(JsonNode arr, int maxItems, int maxLen) {
        ArrayNode out = Json.MAPPER.createArrayNode();
        list(arr, maxItems, maxLen).forEach(out::add);
        return out;
    }
}
