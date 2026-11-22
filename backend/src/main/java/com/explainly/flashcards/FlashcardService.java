package com.explainly.flashcards;

import com.explainly.ai.AiService;
import com.explainly.ai.LlmRequest;
import com.explainly.ai.Prompts;
import com.explainly.common.ApiException;
import com.explainly.explanation.LearningMaterial;
import com.explainly.video.AnalysisAccess;
import com.explainly.video.VideoAnalysis;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

@Service
public class FlashcardService {

    private final FlashcardRepository flashcards;
    private final AnalysisAccess access;
    private final LearningMaterial material;
    private final AiService ai;
    private final TransactionTemplate tx;

    public FlashcardService(FlashcardRepository flashcards, AnalysisAccess access, LearningMaterial material, AiService ai,
                            PlatformTransactionManager txManager) {
        this.flashcards = flashcards;
        this.access = access;
        this.material = material;
        this.ai = ai;
        this.tx = new TransactionTemplate(txManager);
    }

    public record FlashcardDto(Long id, int position, String question, String answer, String definition) {
        static FlashcardDto from(Flashcard f) {
            return new FlashcardDto(f.getId(), f.getPosition(), f.getQuestion(), f.getAnswer(), f.getDefinition());
        }
    }

    @Transactional(readOnly = true)
    public List<FlashcardDto> list(Long userId, Long analysisId) {
        access.requireCompleted(userId, analysisId);
        return flashcards.findByAnalysisIdOrderByPositionAsc(analysisId).stream().map(FlashcardDto::from).toList();
    }

    /** Generates (or regenerates) the flashcard deck for a video. */
    public List<FlashcardDto> generate(Long userId, Long analysisId) {
        String[] input = tx.execute(s -> {
            VideoAnalysis a = access.requireCompleted(userId, analysisId);
            return new String[]{a.getVideo().getTitle(), material.full(analysisId)};
        });
        JsonNode json = ai.completeJson(LlmRequest.json("flashcards", Prompts.TEACHER, Prompts.flashcards(input[0], input[1]), 8000));
        return tx.execute(s -> {
            List<Flashcard> cards = new ArrayList<>();
            for (JsonNode n : json.path("flashcards")) {
                if (cards.size() >= 30) break;
                String q = n.path("question").asText("").trim();
                String ans = n.path("answer").asText("").trim();
                if (q.isEmpty() || ans.isEmpty()) continue;
                Flashcard f = new Flashcard();
                f.setAnalysisId(analysisId);
                f.setPosition(cards.size());
                f.setQuestion(clip(q, 1000));
                f.setAnswer(clip(ans, 2000));
                String def = n.path("definition").isTextual() ? n.path("definition").asText().trim() : "";
                f.setDefinition(def.isEmpty() ? null : clip(def, 1000));
                cards.add(f);
            }
            if (cards.size() < 3) {
                throw ApiException.unprocessable("FLASHCARD_GENERATION_FAILED", "We couldn't create flashcards this time. Please try again.");
            }
            flashcards.deleteByAnalysisId(analysisId);
            flashcards.flush();
            return flashcards.saveAll(cards).stream().map(FlashcardDto::from).toList();
        });
    }

    private static String clip(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
