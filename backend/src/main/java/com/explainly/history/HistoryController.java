package com.explainly.history;

import com.explainly.bookmarks.BookmarkRepository;
import com.explainly.common.CurrentUser;
import com.explainly.common.PageResponse;
import com.explainly.notes.NoteRepository;
import com.explainly.quiz.QuizAttemptRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.TreeMap;

@RestController
@RequestMapping("/api/history")
@Tag(name = "Learning history")
public class HistoryController {

    private final HistoryService history;
    private final LearningHistoryRepository historyRepo;
    private final QuizAttemptRepository attempts;
    private final BookmarkRepository bookmarks;
    private final NoteRepository notes;

    public HistoryController(HistoryService history, LearningHistoryRepository historyRepo, QuizAttemptRepository attempts,
                             BookmarkRepository bookmarks, NoteRepository notes) {
        this.history = history;
        this.historyRepo = historyRepo;
        this.attempts = attempts;
        this.bookmarks = bookmarks;
        this.notes = notes;
    }

    @GetMapping
    @Operation(summary = "Your library of analyzed videos, most recent first")
    public PageResponse<HistoryDtos.HistoryItem> list(@RequestParam(required = false) String category,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "12") int size) {
        return history.list(CurrentUser.id(), category, page, size);
    }

    @GetMapping("/stats")
    @Transactional(readOnly = true)
    public HistoryDtos.Stats stats() {
        Long userId = CurrentUser.id();
        Map<String, Long> categories = new TreeMap<>();
        for (Object[] row : historyRepo.countCategories(userId)) categories.put((String) row[0], (Long) row[1]);
        return new HistoryDtos.Stats(historyRepo.countByUserId(userId), historyRepo.countByUserIdAndProgressPercent(userId, 100),
                attempts.countByUserId(userId), (int) Math.round(attempts.averagePercentForUser(userId)),
                bookmarks.countByUserId(userId), notes.countByUserId(userId), categories);
    }

    @PutMapping("/{analysisId}/progress")
    public HistoryDtos.Progress progress(@PathVariable Long analysisId, @RequestBody HistoryDtos.ProgressUpdate update) {
        return history.updateProgress(CurrentUser.id(), analysisId, update.completedChapterIds(), update.lastPositionSeconds());
    }

    @DeleteMapping("/{analysisId}")
    @Operation(summary = "Remove a video from your library")
    public ResponseEntity<Void> remove(@PathVariable Long analysisId) {
        history.remove(CurrentUser.id(), analysisId);
        return ResponseEntity.noContent().build();
    }
}
