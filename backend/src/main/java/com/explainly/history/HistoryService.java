package com.explainly.history;

import com.explainly.common.ApiException;
import com.explainly.common.Json;
import com.explainly.common.PageResponse;
import com.explainly.explanation.VideoChapterRepository;
import com.explainly.video.VideoAnalysis;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class HistoryService {

    private final LearningHistoryRepository history;
    private final VideoChapterRepository chapters;

    public HistoryService(LearningHistoryRepository history, VideoChapterRepository chapters) {
        this.history = history;
        this.chapters = chapters;
    }

    @Transactional
    public LearningHistory attach(Long userId, VideoAnalysis analysis) {
        LearningHistory h = history.findByUserIdAndAnalysisId(userId, analysis.getId()).orElseGet(() -> {
            LearningHistory n = new LearningHistory();
            n.setUserId(userId);
            n.setAnalysis(analysis);
            return n;
        });
        h.setLastAccessedAt(Instant.now());
        return history.save(h);
    }

    @Transactional
    public void touch(Long userId, Long analysisId) {
        history.findByUserIdAndAnalysisId(userId, analysisId).ifPresent(h -> h.setLastAccessedAt(Instant.now()));
    }

    @Transactional(readOnly = true)
    public PageResponse<HistoryDtos.HistoryItem> list(Long userId, String category, int page, int size) {
        PageRequest pr = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 50), Sort.by(Sort.Direction.DESC, "lastAccessedAt"));
        var result = category == null || category.isBlank()
                ? history.findByUserId(userId, pr)
                : history.findByUserIdAndAnalysisCategory(userId, category, pr);
        return PageResponse.of(result, HistoryDtos.HistoryItem::from);
    }

    @Transactional
    public HistoryDtos.Progress updateProgress(Long userId, Long analysisId, List<Long> completedChapterIds, Integer lastPositionSeconds) {
        LearningHistory h = history.findByUserIdAndAnalysisId(userId, analysisId)
                .orElseThrow(() -> ApiException.notFound("Video analysis"));
        Set<Long> valid = new LinkedHashSet<>();
        Set<Long> chapterIds = new java.util.HashSet<>();
        chapters.findByAnalysisIdOrderByPositionAsc(analysisId).forEach(c -> chapterIds.add(c.getId()));
        if (completedChapterIds != null) completedChapterIds.stream().filter(chapterIds::contains).forEach(valid::add);
        h.setCompletedChaptersJson(Json.write(valid));
        h.setProgressPercent(chapterIds.isEmpty() ? 0 : (int) Math.round(100.0 * valid.size() / chapterIds.size()));
        if (lastPositionSeconds != null && lastPositionSeconds >= 0) h.setLastPositionSeconds(lastPositionSeconds);
        h.setLastAccessedAt(Instant.now());
        return HistoryDtos.Progress.from(h);
    }

    @Transactional
    public void remove(Long userId, Long analysisId) {
        history.findByUserIdAndAnalysisId(userId, analysisId).ifPresent(history::delete);
    }
}
