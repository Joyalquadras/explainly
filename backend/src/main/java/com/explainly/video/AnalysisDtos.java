package com.explainly.video;

import com.explainly.history.HistoryDtos;
import com.explainly.transcript.TranscriptSource;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class AnalysisDtos {
    private AnalysisDtos() {}

    public record AnalyzeRequest(@NotBlank @Size(max = 2048) String url, boolean forceNew) {}

    public record TranscriptTextRequest(@NotBlank @Size(max = 1_500_000) String text) {}

    public record VideoDto(Long id, String youtubeId, String title, String channelTitle, String description,
                           String thumbnailUrl, Integer durationSeconds, Instant publishedAt) {
        public static VideoDto from(Video v) {
            return new VideoDto(v.getId(), v.getYoutubeId(), v.getTitle(), v.getChannelTitle(), v.getDescription(),
                    v.getThumbnailUrl(), v.getDurationSeconds(), v.getPublishedAt());
        }
    }

    public record StatusDto(Long id, AnalysisStatus status, int progress, String statusMessage, String errorMessage,
                            String category, TranscriptSource transcriptSource, boolean canSubmitTranscript,
                            boolean canRetry, Instant updatedAt) {}

    public record SubmitResponse(StatusDto analysis, VideoDto video, boolean reused) {}

    public record Summaries(String oneMinute, String fiveMinute, String detailed) {}

    public record ChapterDto(Long id, int position, String title, Integer startSeconds, Integer endSeconds,
                             String shortSummary, String simpleExplanation, String detailedExplanation,
                             String realWorldExample, String practicalUseCase, List<String> keyPoints,
                             List<String> concepts) {}

    public record IllustrationDto(Long id, Long chapterId, int position, String title, String type, String explanation,
                                  String example, String svg, boolean hasImage) {}

    public record DetailDto(StatusDto status, VideoDto video, JsonNode overview, String beginnerExplanation,
                            String technicalExplanation, JsonNode concepts, Summaries summaries,
                            List<ChapterDto> chapters, List<IllustrationDto> illustrations,
                            HistoryDtos.Progress progress, boolean transcriptTimed, String aiModel) {}

    public record TranscriptView(TranscriptSource source, String language, boolean timed,
                                 List<com.explainly.transcript.TranscriptSegment> segments) {}
}
