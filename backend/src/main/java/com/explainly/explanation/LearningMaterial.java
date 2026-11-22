package com.explainly.explanation;

import com.explainly.common.Json;
import com.explainly.common.TimeFormat;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.List;

/** Builds a compact text digest of the generated explanation, used as grounded context for follow-up AI tasks. */
@Component
public class LearningMaterial {

    private final ExplanationRepository explanations;
    private final VideoChapterRepository chapters;

    public LearningMaterial(ExplanationRepository explanations, VideoChapterRepository chapters) {
        this.explanations = explanations;
        this.chapters = chapters;
    }

    public String overview(Long analysisId) {
        return explanations.findByAnalysisId(analysisId).map(e -> {
            JsonNode o = Json.tree(e.getOverviewJson());
            return o.path("title").asText("") + "\n" + o.path("whatIsItAbout").asText("") + "\nWhy it matters: "
                    + o.path("whyItMatters").asText("");
        }).orElse("");
    }

    public String chapterList(Long analysisId) {
        StringBuilder sb = new StringBuilder();
        for (VideoChapter c : chapters.findByAnalysisIdOrderByPositionAsc(analysisId)) {
            sb.append("- ");
            if (c.getStartSeconds() != null) sb.append('[').append(TimeFormat.clock(c.getStartSeconds())).append("] ");
            sb.append(c.getTitle()).append(": ").append(c.getShortSummary()).append('\n');
        }
        return sb.toString();
    }

    public String full(Long analysisId) {
        StringBuilder sb = new StringBuilder(overview(analysisId)).append("\n\n");
        List<VideoChapter> list = chapters.findByAnalysisIdOrderByPositionAsc(analysisId);
        for (VideoChapter c : list) {
            sb.append("## ");
            if (c.getStartSeconds() != null) sb.append('[').append(TimeFormat.clock(c.getStartSeconds())).append("] ");
            sb.append(c.getTitle()).append(" (startSeconds=").append(c.getStartSeconds()).append(")\n")
                    .append(c.getSimpleExplanation()).append('\n').append(c.getDetailedExplanation()).append('\n')
                    .append("Example: ").append(c.getRealWorldExample()).append('\n')
                    .append("Key points: ").append(String.join("; ", Json.stringList(c.getKeyPointsJson()))).append("\n\n");
        }
        explanations.findByAnalysisId(analysisId).ifPresent(e -> {
            sb.append("## Key concepts\n");
            for (JsonNode c : Json.tree(e.getConceptsJson())) {
                sb.append("- ").append(c.path("term").asText()).append(": ").append(c.path("definition").asText()).append('\n');
            }
        });
        return sb.toString();
    }
}
