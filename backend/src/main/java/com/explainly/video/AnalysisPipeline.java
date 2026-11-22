package com.explainly.video;

import com.explainly.ai.AiProviderException;
import com.explainly.ai.AiService;
import com.explainly.ai.LlmClient;
import com.explainly.ai.TranscriptPreparer;
import com.explainly.common.ApiException;
import com.explainly.common.Json;
import com.explainly.config.AsyncConfig;
import com.explainly.explanation.*;
import com.explainly.illustration.Illustration;
import com.explainly.illustration.IllustrationGenerator;
import com.explainly.illustration.IllustrationRepository;
import com.explainly.transcript.*;
import com.explainly.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Asynchronous analysis pipeline. Each step commits its own short transaction so progress is visible to
 * clients while long AI calls run outside any transaction.
 */
@Component
public class AnalysisPipeline {

    private static final Logger log = LoggerFactory.getLogger(AnalysisPipeline.class);

    private final VideoAnalysisRepository analyses;
    private final VideoRepository videos;
    private final TranscriptRepository transcripts;
    private final TranscriptService transcriptService;
    private final List<TranscriptProvider> providers;
    private final UserRepository users;
    private final AiService ai;
    private final TranscriptPreparer preparer;
    private final ExplanationGenerator explanationGenerator;
    private final IllustrationGenerator illustrationGenerator;
    private final ExplanationRepository explanations;
    private final VideoChapterRepository chapters;
    private final IllustrationRepository illustrations;
    private final com.explainly.ai.MediaAiClient media;
    private final TransactionTemplate tx;
    private final Executor executor;
    private final Set<Long> running = ConcurrentHashMap.newKeySet();

    public AnalysisPipeline(VideoAnalysisRepository analyses, VideoRepository videos, TranscriptRepository transcripts,
                            TranscriptService transcriptService, List<TranscriptProvider> providers, UserRepository users,
                            AiService ai, TranscriptPreparer preparer, ExplanationGenerator explanationGenerator,
                            IllustrationGenerator illustrationGenerator, ExplanationRepository explanations,
                            VideoChapterRepository chapters, IllustrationRepository illustrations,
                            com.explainly.ai.MediaAiClient media, PlatformTransactionManager txManager,
                            @org.springframework.beans.factory.annotation.Qualifier(AsyncConfig.ANALYSIS_EXECUTOR) Executor executor) {
        this.analyses = analyses;
        this.videos = videos;
        this.transcripts = transcripts;
        this.transcriptService = transcriptService;
        this.providers = providers;
        this.users = users;
        this.ai = ai;
        this.preparer = preparer;
        this.explanationGenerator = explanationGenerator;
        this.illustrationGenerator = illustrationGenerator;
        this.explanations = explanations;
        this.chapters = chapters;
        this.illustrations = illustrations;
        this.media = media;
        this.tx = new TransactionTemplate(txManager);
        this.executor = executor;
    }

    @Async(AsyncConfig.ANALYSIS_EXECUTOR)
    @TransactionalEventListener(fallbackExecution = true)
    public void onAnalysisRequested(AnalysisRequestedEvent event) {
        run(event.analysisId());
    }

    /** Re-queues analyses interrupted by a restart. */
    @EventListener(ApplicationReadyEvent.class)
    public void resumeInterrupted() {
        List<VideoAnalysis> interrupted = analyses.findByStatusIn(AnalysisStatus.IN_FLIGHT);
        if (!interrupted.isEmpty()) log.info("Resuming {} interrupted analyses", interrupted.size());
        interrupted.forEach(a -> executor.execute(() -> run(a.getId())));
    }

    public void run(Long analysisId) {
        if (!running.add(analysisId)) return;
        try {
            execute(analysisId);
        } catch (AiProviderException e) {
            fail(analysisId, e.getMessage());
        } catch (ApiException e) {
            fail(analysisId, e.getMessage());
        } catch (Exception e) {
            log.error("Analysis {} failed", analysisId, e);
            fail(analysisId, "Something went wrong while preparing this video. Please try again.");
        } finally {
            running.remove(analysisId);
        }
    }

    private record Snapshot(Long videoId, Long transcriptId, Long createdById) {}

    private void execute(Long id) {
        Snapshot snap = tx.execute(s -> {
            VideoAnalysis a = analyses.findById(id).orElse(null);
            if (a == null || !AnalysisStatus.IN_FLIGHT.contains(a.getStatus())) return null;
            a.setStartedAt(Instant.now());
            a.setErrorMessage(null);
            return new Snapshot(a.getVideo().getId(), a.getTranscript() == null ? null : a.getTranscript().getId(),
                    a.getCreatedBy() == null ? null : a.getCreatedBy().getId());
        });
        if (snap == null) return;
        Video video = videos.findById(snap.videoId()).orElseThrow();
        Long transcriptId = snap.transcriptId();

        // 1. Transcript
        if (transcriptId == null) {
            update(id, AnalysisStatus.FETCHING_TRANSCRIPT, AnalysisStatus.FETCHING_TRANSCRIPT.baseProgress(), "Looking for a transcript…");
            List<String> reasons = new ArrayList<>();
            TranscriptProvider.FetchedTranscript found = null;
            for (TranscriptProvider provider : providers) {
                try {
                    TranscriptProvider.Attempt attempt = provider.fetch(video, snap.createdById());
                    if (attempt.transcript().isPresent()) {
                        found = attempt.transcript().get();
                        break;
                    }
                    reasons.add(attempt.unavailableReason());
                } catch (Exception e) {
                    log.warn("Transcript provider {} failed for analysis {}: {}", provider.name(), id, e.getMessage());
                    reasons.add(provider.name() + " is temporarily unavailable.");
                }
            }
            if (found == null) {
                String message = "We couldn't get this video's transcript automatically. YouTube only shares caption files "
                        + "with the video's owner. " + String.join(" ", reasons)
                        + " You can paste the transcript or upload a caption file (.srt, .vtt, .txt)"
                        + (media.transcriptionEnabled() ? ", or upload the audio," : "") + " to continue.";
                tx.executeWithoutResult(s -> analyses.findById(id).ifPresent(a -> {
                    a.setStatus(AnalysisStatus.AWAITING_TRANSCRIPT);
                    a.setProgress(AnalysisStatus.AWAITING_TRANSCRIPT.baseProgress());
                    a.setStatusMessage(message.length() > 500 ? message.substring(0, 500) : message);
                }));
                return;
            }
            TranscriptProvider.FetchedTranscript fetched = found;
            transcriptId = tx.execute(s -> {
                Transcript t = transcriptService.store(video, fetched.source(), fetched.language(), fetched.segments(),
                        snap.createdById() == null ? null : users.getReferenceById(snap.createdById()));
                analyses.findById(id).ifPresent(a -> a.setTranscript(t));
                return t.getId();
            });
        }

        // 2. Reuse identical work
        Long finalTranscriptId = transcriptId;
        Boolean reused = tx.execute(s -> copyFromCompleted(id, finalTranscriptId));
        if (Boolean.TRUE.equals(reused)) {
            complete(id, "Ready (reused an earlier analysis of the same transcript)");
            return;
        }

        LlmClient client = ai.requireClient();

        // 3. Prepare transcript
        update(id, AnalysisStatus.PROCESSING, AnalysisStatus.PROCESSING.baseProgress(), "Reading the whole transcript…");
        Transcript transcript = transcripts.findById(transcriptId).orElseThrow();
        TranscriptPreparer.Prepared prepared = preparer.prepare(transcript);

        // 4. Explanations
        update(id, AnalysisStatus.GENERATING_EXPLANATION, 40, "Breaking the video into topics and concepts…");
        ExplanationGenerator.Structure structure = explanationGenerator.generateStructure(video, prepared);
        update(id, AnalysisStatus.GENERATING_EXPLANATION, 60, "Writing beginner and technical explanations…");
        ExplanationGenerator.LearningModes modes = explanationGenerator.generateModes(video, prepared, structure.chapters());

        List<VideoChapter> savedChapters = tx.execute(s -> {
            VideoAnalysis a = analyses.findById(id).orElseThrow();
            explanations.deleteByAnalysisId(id);
            illustrations.deleteByAnalysisId(id);
            chapters.deleteByAnalysisId(id);
            explanations.flush();
            Explanation e = new Explanation();
            e.setAnalysis(a);
            e.setOverviewJson(Json.write(structure.overview()));
            e.setConceptsJson(Json.write(structure.concepts()));
            e.setBeginnerExplanation(modes.beginner());
            e.setTechnicalExplanation(modes.technical());
            e.setSummaryShort(modes.oneMinute());
            e.setSummaryMedium(modes.fiveMinute());
            e.setSummaryDetailed(modes.detailed());
            e.setGeneratedNotes(modes.notes());
            explanations.save(e);
            List<VideoChapter> list = new ArrayList<>();
            int pos = 0;
            for (ExplanationGenerator.ChapterDraft d : structure.chapters()) {
                VideoChapter c = new VideoChapter();
                c.setAnalysis(a);
                c.setPosition(pos++);
                c.setTitle(d.title());
                c.setStartSeconds(d.startSeconds());
                c.setEndSeconds(d.endSeconds());
                c.setShortSummary(d.shortSummary());
                c.setSimpleExplanation(d.simpleExplanation());
                c.setDetailedExplanation(d.detailedExplanation());
                c.setRealWorldExample(d.realWorldExample());
                c.setPracticalUseCase(d.practicalUseCase());
                c.setKeyPointsJson(Json.write(d.keyPoints()));
                c.setConceptsJson(Json.write(d.concepts()));
                list.add(chapters.save(c));
            }
            a.setCategory(structure.category());
            a.setAiProvider(client.id());
            a.setAiModel(client.model());
            return list;
        });

        // 5. Illustrations (best effort: a failure here does not fail the whole analysis)
        update(id, AnalysisStatus.GENERATING_ILLUSTRATIONS, 80, "Drawing diagrams and illustrations…");
        String completionMessage = "Ready";
        try {
            List<IllustrationGenerator.Generated> generated = illustrationGenerator.generate(video.getTitle(), savedChapters);
            tx.executeWithoutResult(s -> {
                VideoAnalysis a = analyses.findById(id).orElseThrow();
                int pos = 0;
                for (IllustrationGenerator.Generated g : generated) {
                    Illustration ill = new Illustration();
                    ill.setAnalysis(a);
                    if (g.chapterIndex() != null) ill.setChapter(chapters.getReferenceById(savedChapters.get(g.chapterIndex()).getId()));
                    ill.setPosition(pos++);
                    ill.setTitle(g.title());
                    ill.setDiagramType(g.type());
                    ill.setExplanation(g.explanation());
                    ill.setExample(g.example());
                    ill.setSpecJson(g.specJson());
                    ill.setSvg(g.svg());
                    if (g.imageBase64() != null) {
                        ill.setImageData(g.imageBase64());
                        ill.setImageMime("image/png");
                    }
                    illustrations.save(ill);
                }
            });
        } catch (Exception e) {
            log.warn("Illustrations failed for analysis {}: {}", id, e.getMessage());
            completionMessage = "Ready (diagrams could not be generated this time)";
        }
        complete(id, completionMessage);
    }

    /** Copies generated content from a completed analysis of the identical transcript, if one exists. */
    private boolean copyFromCompleted(Long id, Long transcriptId) {
        Optional<VideoAnalysis> source = analyses.findCompletedWithTranscript(transcriptId, id).stream().findFirst();
        if (source.isEmpty()) return false;
        VideoAnalysis target = analyses.findById(id).orElseThrow();
        Long sourceId = source.get().getId();
        Explanation se = explanations.findByAnalysisId(sourceId).orElse(null);
        if (se == null) return false;
        Explanation e = new Explanation();
        e.setAnalysis(target);
        e.setOverviewJson(se.getOverviewJson());
        e.setConceptsJson(se.getConceptsJson());
        e.setBeginnerExplanation(se.getBeginnerExplanation());
        e.setTechnicalExplanation(se.getTechnicalExplanation());
        e.setSummaryShort(se.getSummaryShort());
        e.setSummaryMedium(se.getSummaryMedium());
        e.setSummaryDetailed(se.getSummaryDetailed());
        e.setGeneratedNotes(se.getGeneratedNotes());
        explanations.save(e);
        Map<Long, VideoChapter> chapterMap = new HashMap<>();
        for (VideoChapter sc : chapters.findByAnalysisIdOrderByPositionAsc(sourceId)) {
            VideoChapter c = new VideoChapter();
            c.setAnalysis(target);
            c.setPosition(sc.getPosition());
            c.setTitle(sc.getTitle());
            c.setStartSeconds(sc.getStartSeconds());
            c.setEndSeconds(sc.getEndSeconds());
            c.setShortSummary(sc.getShortSummary());
            c.setSimpleExplanation(sc.getSimpleExplanation());
            c.setDetailedExplanation(sc.getDetailedExplanation());
            c.setRealWorldExample(sc.getRealWorldExample());
            c.setPracticalUseCase(sc.getPracticalUseCase());
            c.setKeyPointsJson(sc.getKeyPointsJson());
            c.setConceptsJson(sc.getConceptsJson());
            chapterMap.put(sc.getId(), chapters.save(c));
        }
        for (Illustration si : illustrations.findByAnalysisIdOrderByPositionAsc(sourceId)) {
            Illustration i = new Illustration();
            i.setAnalysis(target);
            i.setChapter(si.getChapter() == null ? null : chapterMap.get(si.getChapter().getId()));
            i.setPosition(si.getPosition());
            i.setTitle(si.getTitle());
            i.setDiagramType(si.getDiagramType());
            i.setExplanation(si.getExplanation());
            i.setExample(si.getExample());
            i.setSpecJson(si.getSpecJson());
            i.setSvg(si.getSvg());
            i.setImageData(si.getImageData());
            i.setImageMime(si.getImageMime());
            illustrations.save(i);
        }
        target.setCategory(source.get().getCategory());
        target.setAiProvider(source.get().getAiProvider());
        target.setAiModel(source.get().getAiModel());
        return true;
    }

    private void update(Long id, AnalysisStatus status, int progress, String message) {
        tx.executeWithoutResult(s -> analyses.findById(id).ifPresent(a -> {
            a.setStatus(status);
            a.setProgress(progress);
            a.setStatusMessage(message);
        }));
    }

    private void complete(Long id, String message) {
        tx.executeWithoutResult(s -> analyses.findById(id).ifPresent(a -> {
            a.setStatus(AnalysisStatus.COMPLETED);
            a.setProgress(100);
            a.setStatusMessage(message);
            a.setCompletedAt(Instant.now());
        }));
    }

    private void fail(Long id, String message) {
        tx.executeWithoutResult(s -> analyses.findById(id).ifPresent(a -> {
            a.setStatus(AnalysisStatus.FAILED);
            a.setErrorMessage(message);
            a.setStatusMessage("Analysis failed");
        }));
    }
}
