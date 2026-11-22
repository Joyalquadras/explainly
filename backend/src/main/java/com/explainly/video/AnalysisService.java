package com.explainly.video;

import com.explainly.ai.MediaAiClient;
import com.explainly.common.ApiException;
import com.explainly.common.CurrentUser;
import com.explainly.common.Json;
import com.explainly.config.AppProperties;
import com.explainly.explanation.ExplanationRepository;
import com.explainly.explanation.VideoChapterRepository;
import com.explainly.history.HistoryDtos;
import com.explainly.history.HistoryService;
import com.explainly.history.LearningHistoryRepository;
import com.explainly.illustration.IllustrationRepository;
import com.explainly.transcript.*;
import com.explainly.user.UserRepository;
import com.explainly.video.AnalysisDtos.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class AnalysisService {

    private static final Set<String> AUDIO_EXTENSIONS = Set.of("mp3", "m4a", "wav", "webm", "mp4", "mpeg", "mpga", "ogg", "flac");
    private final AnalysisAccess access;
    private final VideoAnalysisRepository analyses;
    private final ExplanationRepository explanations;
    private final VideoChapterRepository chapters;
    private final IllustrationRepository illustrations;
    private final LearningHistoryRepository historyRepo;
    private final HistoryService history;
    private final TranscriptService transcriptService;
    private final MediaAiClient media;
    private final UserRepository users;
    private final ApplicationEventPublisher events;
    private final AppProperties props;

    public AnalysisService(AnalysisAccess access, VideoAnalysisRepository analyses, ExplanationRepository explanations,
                           VideoChapterRepository chapters, IllustrationRepository illustrations,
                           LearningHistoryRepository historyRepo, HistoryService history, TranscriptService transcriptService,
                           MediaAiClient media, UserRepository users, ApplicationEventPublisher events, AppProperties props) {
        this.access = access;
        this.analyses = analyses;
        this.explanations = explanations;
        this.chapters = chapters;
        this.illustrations = illustrations;
        this.historyRepo = historyRepo;
        this.history = history;
        this.transcriptService = transcriptService;
        this.media = media;
        this.users = users;
        this.events = events;
        this.props = props;
    }

    @Transactional(readOnly = true)
    public StatusDto status(Long userId, Long analysisId) {
        return toStatus(access.require(userId, analysisId), userId);
    }

    @Transactional
    public DetailDto detail(Long userId, Long analysisId) {
        VideoAnalysis a = access.require(userId, analysisId);
        history.touch(userId, analysisId);
        StatusDto status = toStatus(a, userId);
        HistoryDtos.Progress progress = historyRepo.findByUserIdAndAnalysisId(userId, analysisId)
                .map(HistoryDtos.Progress::from).orElse(new HistoryDtos.Progress(0, List.of(), null));
        boolean timed = a.getTranscript() != null && TranscriptParser.isTimed(TranscriptService.segments(a.getTranscript()));
        var explanation = explanations.findByAnalysisId(analysisId).orElse(null);
        List<ChapterDto> chapterDtos = chapters.findByAnalysisIdOrderByPositionAsc(analysisId).stream()
                .map(c -> new ChapterDto(c.getId(), c.getPosition(), c.getTitle(), c.getStartSeconds(), c.getEndSeconds(),
                        c.getShortSummary(), c.getSimpleExplanation(), c.getDetailedExplanation(), c.getRealWorldExample(),
                        c.getPracticalUseCase(), Json.stringList(c.getKeyPointsJson()), Json.stringList(c.getConceptsJson())))
                .toList();
        List<IllustrationDto> illustrationDtos = illustrations.findByAnalysisIdOrderByPositionAsc(analysisId).stream()
                .map(i -> new IllustrationDto(i.getId(), i.getChapter() == null ? null : i.getChapter().getId(), i.getPosition(),
                        i.getTitle(), i.getDiagramType(), i.getExplanation(), i.getExample(), i.getSvg(), i.getImageData() != null))
                .toList();
        return new DetailDto(status, VideoDto.from(a.getVideo()),
                explanation == null ? null : Json.tree(explanation.getOverviewJson()),
                explanation == null ? null : explanation.getBeginnerExplanation(),
                explanation == null ? null : explanation.getTechnicalExplanation(),
                explanation == null ? Json.MAPPER.createArrayNode() : Json.tree(explanation.getConceptsJson()),
                explanation == null ? null : new Summaries(explanation.getSummaryShort(), explanation.getSummaryMedium(), explanation.getSummaryDetailed()),
                chapterDtos, illustrationDtos, progress, timed, a.getAiModel());
    }

    @Transactional(readOnly = true)
    public TranscriptView transcript(Long userId, Long analysisId) {
        VideoAnalysis a = access.require(userId, analysisId);
        Transcript t = a.getTranscript();
        if (t == null) throw ApiException.notFound("Transcript");
        List<TranscriptSegment> segments = TranscriptService.segments(t);
        return new TranscriptView(t.getSource(), t.getLanguage(), TranscriptParser.isTimed(segments), segments);
    }

    @Transactional
    public StatusDto submitTranscriptText(Long userId, Long analysisId, String text) {
        VideoAnalysis a = requireTranscriptSubmittable(userId, analysisId);
        return attachTranscript(userId, a, TranscriptSource.USER_PASTE, transcriptService.parseText(text));
    }

    @Transactional
    public StatusDto submitTranscriptFile(Long userId, Long analysisId, MultipartFile file) {
        VideoAnalysis a = requireTranscriptSubmittable(userId, analysisId);
        return attachTranscript(userId, a, TranscriptSource.USER_UPLOAD, transcriptService.parseUpload(file));
    }

    @Transactional
    public StatusDto submitAudio(Long userId, Long analysisId, MultipartFile file) {
        VideoAnalysis a = requireTranscriptSubmittable(userId, analysisId);
        if (!media.transcriptionEnabled()) {
            throw ApiException.serviceUnavailable("TRANSCRIPTION_NOT_CONFIGURED", "Audio transcription is not enabled on this server.");
        }
        if (file == null || file.isEmpty()) throw ApiException.badRequest("EMPTY_FILE", "Please choose an audio file.");
        if (file.getSize() > props.limits().maxAudioUploadBytes()) {
            throw ApiException.badRequest("FILE_TOO_LARGE", "Audio files must be smaller than "
                    + props.limits().maxAudioUploadBytes() / 1024 / 1024 + " MB.");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : "";
        if (!AUDIO_EXTENSIONS.contains(ext)) {
            throw ApiException.badRequest("UNSUPPORTED_FILE_TYPE", "Please upload an audio file (mp3, m4a, wav, webm, ogg or flac).");
        }
        try {
            List<TranscriptSegment> segments = media.transcribe(file.getBytes(), "audio." + ext);
            return attachTranscript(userId, a, TranscriptSource.AUDIO_TRANSCRIPTION, segments);
        } catch (java.io.IOException e) {
            throw ApiException.badRequest("UPLOAD_FAILED", "We couldn't read that file. Please try again.");
        } catch (com.explainly.ai.AiProviderException e) {
            throw ApiException.unprocessable("TRANSCRIPTION_FAILED", e.getMessage());
        }
    }

    @Transactional
    public StatusDto retry(Long userId, Long analysisId) {
        VideoAnalysis a = access.require(userId, analysisId);
        requireOwner(userId, a);
        if (a.getStatus() != AnalysisStatus.FAILED) {
            throw ApiException.conflict("NOT_RETRYABLE", "Only failed analyses can be retried.");
        }
        a.setStatus(AnalysisStatus.PENDING);
        a.setProgress(AnalysisStatus.PENDING.baseProgress());
        a.setStatusMessage("Queued for analysis");
        a.setErrorMessage(null);
        events.publishEvent(new AnalysisRequestedEvent(a.getId()));
        return toStatus(a, userId);
    }

    private StatusDto attachTranscript(Long userId, VideoAnalysis a, TranscriptSource source, List<TranscriptSegment> segments) {
        Transcript t = transcriptService.store(a.getVideo(), source, null, segments, users.getReferenceById(userId));
        a.setTranscript(t);
        a.setStatus(AnalysisStatus.PENDING);
        a.setProgress(AnalysisStatus.PENDING.baseProgress());
        a.setStatusMessage("Transcript received. Queued for analysis");
        a.setErrorMessage(null);
        events.publishEvent(new AnalysisRequestedEvent(a.getId()));
        return toStatus(a, userId);
    }

    private VideoAnalysis requireTranscriptSubmittable(Long userId, Long analysisId) {
        VideoAnalysis a = access.require(userId, analysisId);
        requireOwner(userId, a);
        if (!EnumSet.of(AnalysisStatus.AWAITING_TRANSCRIPT, AnalysisStatus.FAILED).contains(a.getStatus())) {
            throw ApiException.conflict("TRANSCRIPT_NOT_EXPECTED", "This video already has a transcript.");
        }
        return a;
    }

    private void requireOwner(Long userId, VideoAnalysis a) {
        boolean owner = a.getCreatedBy() != null && a.getCreatedBy().getId().equals(userId);
        if (!owner && !CurrentUser.isAdmin()) {
            throw ApiException.forbidden("Only the person who started this analysis can change it. Analyze the video again to create your own.");
        }
    }

    StatusDto toStatus(VideoAnalysis a, Long userId) {
        boolean owner = a.getCreatedBy() != null && a.getCreatedBy().getId().equals(userId);
        boolean awaiting = a.getStatus() == AnalysisStatus.AWAITING_TRANSCRIPT || a.getStatus() == AnalysisStatus.FAILED;
        return new StatusDto(a.getId(), a.getStatus(), a.getProgress(), a.getStatusMessage(), a.getErrorMessage(),
                a.getCategory(), a.getTranscript() == null ? null : a.getTranscript().getSource(),
                owner && awaiting, owner && a.getStatus() == AnalysisStatus.FAILED, a.getUpdatedAt());
    }
}
