package com.explainly.flashcards;

import com.explainly.common.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/analyses/{analysisId}/flashcards")
@Tag(name = "Flashcards")
public class FlashcardController {

    private final FlashcardService flashcards;

    public FlashcardController(FlashcardService flashcards) {
        this.flashcards = flashcards;
    }

    @GetMapping
    public List<FlashcardService.FlashcardDto> list(@PathVariable Long analysisId) {
        return flashcards.list(CurrentUser.id(), analysisId);
    }

    @PostMapping
    public List<FlashcardService.FlashcardDto> generate(@PathVariable Long analysisId) {
        return flashcards.generate(CurrentUser.id(), analysisId);
    }
}
