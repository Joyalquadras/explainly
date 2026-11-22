package com.explainly.quiz;

import com.explainly.common.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Quizzes")
public class QuizController {

    private final QuizService quizzes;

    public QuizController(QuizService quizzes) {
        this.quizzes = quizzes;
    }

    public record SubmitAttemptRequest(@NotNull @Size(max = 50) List<Integer> answers) {}

    @GetMapping("/analyses/{analysisId}/quiz")
    @Operation(summary = "Latest quiz for the video (204 when none has been generated yet)")
    public ResponseEntity<QuizService.QuizDto> latest(@PathVariable Long analysisId) {
        return quizzes.latest(CurrentUser.id(), analysisId).map(ResponseEntity::ok).orElse(ResponseEntity.noContent().build());
    }

    @PostMapping("/analyses/{analysisId}/quiz")
    @Operation(summary = "Generate a new quiz from the video content")
    public QuizService.QuizDto generate(@PathVariable Long analysisId) {
        return quizzes.generate(CurrentUser.id(), analysisId);
    }

    @PostMapping("/quizzes/{quizId}/attempts")
    public QuizService.AttemptResult submit(@PathVariable Long quizId, @Valid @RequestBody SubmitAttemptRequest req) {
        return quizzes.submit(CurrentUser.id(), quizId, req.answers());
    }

    @GetMapping("/quizzes/{quizId}/attempts")
    public List<QuizService.AttemptSummary> attempts(@PathVariable Long quizId) {
        return quizzes.attempts(CurrentUser.id(), quizId);
    }
}
