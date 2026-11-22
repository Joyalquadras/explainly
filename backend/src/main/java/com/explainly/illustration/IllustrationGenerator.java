package com.explainly.illustration;

import com.explainly.ai.AiService;
import com.explainly.ai.LlmRequest;
import com.explainly.ai.MediaAiClient;
import com.explainly.ai.Prompts;
import com.explainly.common.Json;
import com.explainly.config.AppProperties;
import com.explainly.explanation.VideoChapter;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Asks the model for diagram specs, validates them and renders SVG; optionally adds AI images. */
@Component
public class IllustrationGenerator {

    private static final Logger log = LoggerFactory.getLogger(IllustrationGenerator.class);
    private final AiService ai;
    private final SvgDiagramRenderer renderer;
    private final MediaAiClient media;
    private final AppProperties props;

    public IllustrationGenerator(AiService ai, SvgDiagramRenderer renderer, MediaAiClient media, AppProperties props) {
        this.ai = ai;
        this.renderer = renderer;
        this.media = media;
        this.props = props;
    }

    public record Generated(Integer chapterIndex, String title, String type, String explanation, String example,
                            String specJson, String svg, String imageBase64) {}

    public List<Generated> generate(String videoTitle, List<VideoChapter> chapters) {
        StringBuilder digest = new StringBuilder();
        for (int i = 0; i < chapters.size(); i++) {
            VideoChapter c = chapters.get(i);
            digest.append(i).append(": ").append(c.getTitle()).append(" - ").append(c.getSimpleExplanation())
                    .append(" - ").append(String.join("; ", Json.stringList(c.getKeyPointsJson()))).append('\n');
        }
        int max = Math.min(12, chapters.size() + 1);
        boolean images = media.imagesEnabled();
        JsonNode json = ai.completeJson(LlmRequest.json("illustrations", Prompts.TEACHER,
                Prompts.illustrations(videoTitle, digest.toString(), max, images), 16000));

        List<Generated> result = new ArrayList<>();
        int imagesLeft = props.ai().images().maxPerVideo();
        for (JsonNode n : json.path("illustrations")) {
            if (result.size() >= max) break;
            Optional<DiagramSpec> spec = DiagramSpec.fromJson(n);
            if (spec.isEmpty()) {
                log.debug("Skipping invalid diagram spec: {}", n);
                continue;
            }
            Integer chapterIndex = n.path("chapterIndex").isInt() ? n.path("chapterIndex").asInt() : null;
            if (chapterIndex != null && (chapterIndex < 0 || chapterIndex >= chapters.size())) chapterIndex = null;
            String image = null;
            String imagePrompt = n.path("imagePrompt").asText("");
            if (images && imagesLeft > 0 && !imagePrompt.isBlank()) {
                image = media.generateImage(imagePrompt.length() > 900 ? imagePrompt.substring(0, 900) : imagePrompt).orElse(null);
                if (image != null) imagesLeft--;
            }
            result.add(new Generated(chapterIndex, spec.get().title(), spec.get().type(),
                    clip(n.path("explanation").asText(""), 1500), clip(n.path("example").asText(""), 1500),
                    Json.write(spec.get()), renderer.render(spec.get()), image));
        }
        return result;
    }

    private static String clip(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
