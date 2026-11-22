package com.explainly.history;

import com.explainly.common.Json;
import com.explainly.video.AnalysisStatus;
import com.explainly.video.Video;
import com.explainly.video.VideoAnalysis;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class HistoryDtos {
    private HistoryDtos() {}

    public record HistoryItem(Long analysisId, String youtubeId, String title, String channelTitle, String thumbnailUrl,
                              Integer durationSeconds, AnalysisStatus status, String category, int progressPercent,
                              Integer lastPositionSeconds, Instant lastAccessedAt) {
        public static HistoryItem from(LearningHistory h) {
            VideoAnalysis a = h.getAnalysis();
            Video v = a.getVideo();
            return new HistoryItem(a.getId(), v.getYoutubeId(), v.getTitle(), v.getChannelTitle(), v.getThumbnailUrl(),
                    v.getDurationSeconds(), a.getStatus(), a.getCategory(), h.getProgressPercent(), h.getLastPositionSeconds(),
                    h.getLastAccessedAt());
        }
    }

    public record Progress(int progressPercent, List<Long> completedChapterIds, Integer lastPositionSeconds) {
        public static Progress from(LearningHistory h) {
            return new Progress(h.getProgressPercent(), Json.longList(h.getCompletedChaptersJson()), h.getLastPositionSeconds());
        }
    }

    public record ProgressUpdate(List<Long> completedChapterIds, Integer lastPositionSeconds) {}

    public record Stats(long videos, long completedVideos, long quizAttempts, int averageQuizScore, long bookmarks,
                        long notes, Map<String, Long> categories) {}
}
