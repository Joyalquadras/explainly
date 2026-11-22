package com.explainly.video;

import com.explainly.common.ApiException;
import com.explainly.config.AppProperties;
import com.explainly.history.HistoryService;
import com.explainly.history.LearningHistory;
import com.explainly.history.LearningHistoryRepository;
import com.explainly.user.Role;
import com.explainly.user.User;
import com.explainly.user.UserRepository;
import com.explainly.youtube.VideoMetadata;
import com.explainly.youtube.YouTubeMetadataClient;
import com.explainly.youtube.YouTubeUrlParser;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class VideoService {

    private static final Duration METADATA_TTL = Duration.ofDays(7);
    private final VideoRepository videos;
    private final VideoAnalysisRepository analyses;
    private final LearningHistoryRepository historyRepo;
    private final HistoryService history;
    private final UserRepository users;
    private final YouTubeMetadataClient metadataClient;
    private final ApplicationEventPublisher events;
    private final AppProperties props;

    public VideoService(VideoRepository videos, VideoAnalysisRepository analyses, LearningHistoryRepository historyRepo,
                        HistoryService history, UserRepository users, YouTubeMetadataClient metadataClient,
                        ApplicationEventPublisher events, AppProperties props) {
        this.videos = videos;
        this.analyses = analyses;
        this.historyRepo = historyRepo;
        this.history = history;
        this.users = users;
        this.metadataClient = metadataClient;
        this.events = events;
        this.props = props;
    }

    public record SubmitResult(VideoAnalysis analysis, boolean reused) {}

    /**
     * Submits a video for analysis. Returns the user's existing analysis, attaches a previously completed
     * analysis built from official captions, or starts a new asynchronous analysis.
     */
    @Transactional
    public SubmitResult submit(Long userId, String url, boolean forceNew) {
        YouTubeUrlParser.ParsedUrl parsed = YouTubeUrlParser.parse(url);
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        Video video = upsertVideo(parsed.videoId());

        if (!forceNew) {
            List<LearningHistory> mine = historyRepo.findByUserIdAndVideoId(userId, video.getId());
            Optional<LearningHistory> usable = mine.stream().filter(h -> h.getAnalysis().getStatus() != AnalysisStatus.FAILED).findFirst();
            if (usable.isPresent()) {
                history.attach(userId, usable.get().getAnalysis());
                return new SubmitResult(usable.get().getAnalysis(), true);
            }
            Optional<VideoAnalysis> shared = analyses.findCompletedFromOfficialCaptions(video.getId()).stream().findFirst();
            if (shared.isPresent()) {
                history.attach(userId, shared.get());
                return new SubmitResult(shared.get(), true);
            }
        }

        enforceDailyLimit(user);
        VideoAnalysis analysis = new VideoAnalysis();
        analysis.setVideo(video);
        analysis.setCreatedBy(user);
        analysis.setStatus(AnalysisStatus.PENDING);
        analysis.setProgress(AnalysisStatus.PENDING.baseProgress());
        analysis.setStatusMessage("Queued for analysis");
        analyses.save(analysis);
        history.attach(userId, analysis);
        events.publishEvent(new AnalysisRequestedEvent(analysis.getId()));
        return new SubmitResult(analysis, false);
    }

    @Transactional
    public Video upsertVideo(String youtubeId) {
        Optional<Video> existing = videos.findByYoutubeId(youtubeId);
        if (existing.isPresent() && existing.get().getUpdatedAt().isAfter(Instant.now().minus(METADATA_TTL))) {
            return existing.get();
        }
        VideoMetadata m = metadataClient.fetch(youtubeId);
        Video v = existing.orElseGet(Video::new);
        v.setYoutubeId(youtubeId);
        v.setTitle(m.title() == null || m.title().isBlank() ? "YouTube video" : truncate(m.title(), 500));
        v.setChannelTitle(truncate(m.channelTitle(), 255));
        if (m.description() != null) v.setDescription(m.description());
        v.setThumbnailUrl(m.thumbnailUrl());
        if (m.durationSeconds() != null) v.setDurationSeconds(m.durationSeconds());
        if (m.publishedAt() != null) v.setPublishedAt(m.publishedAt());
        v.setUpdatedAt(Instant.now());
        return videos.save(v);
    }

    private void enforceDailyLimit(User user) {
        int limit = user.getRole() == Role.GUEST ? props.limits().analysesPerDayGuest()
                : user.getRole() == Role.ADMIN ? Integer.MAX_VALUE : props.limits().analysesPerDayUser();
        long today = analyses.countByCreatedByIdAndCreatedAtAfter(user.getId(), Instant.now().minus(Duration.ofDays(1)));
        if (today >= limit) {
            throw ApiException.tooManyRequests(user.getRole() == Role.GUEST
                    ? "Guests can analyze " + limit + " videos per day. Create a free account to analyze more."
                    : "You've reached today's limit of " + limit + " new videos. Please try again tomorrow.");
        }
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
