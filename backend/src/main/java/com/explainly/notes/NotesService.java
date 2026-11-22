package com.explainly.notes;

import com.explainly.common.ApiException;
import com.explainly.explanation.ExplanationRepository;
import com.explainly.video.AnalysisAccess;
import com.explainly.video.VideoAnalysis;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class NotesService {

    private final NoteRepository notes;
    private final ExplanationRepository explanations;
    private final AnalysisAccess access;
    private final MarkdownPdfRenderer pdf;

    public NotesService(NoteRepository notes, ExplanationRepository explanations, AnalysisAccess access, MarkdownPdfRenderer pdf) {
        this.notes = notes;
        this.explanations = explanations;
        this.access = access;
        this.pdf = pdf;
    }

    /** @param edited false while the user still sees the AI-generated notes */
    public record NoteDto(String content, boolean edited, Instant updatedAt) {}

    public record PdfFile(String filename, byte[] bytes) {}

    @Transactional(readOnly = true)
    public NoteDto get(Long userId, Long analysisId) {
        access.requireCompleted(userId, analysisId);
        return notes.findByUserIdAndAnalysisId(userId, analysisId)
                .map(n -> new NoteDto(n.getContent(), true, n.getUpdatedAt()))
                .orElseGet(() -> new NoteDto(generated(analysisId), false, null));
    }

    @Transactional
    public NoteDto save(Long userId, Long analysisId, String content) {
        access.requireCompleted(userId, analysisId);
        Note note = notes.findByUserIdAndAnalysisId(userId, analysisId).orElseGet(() -> {
            Note n = new Note();
            n.setUserId(userId);
            n.setAnalysisId(analysisId);
            return n;
        });
        note.setContent(content);
        note = notes.saveAndFlush(note);
        return new NoteDto(note.getContent(), true, note.getUpdatedAt());
    }

    @Transactional
    public NoteDto reset(Long userId, Long analysisId) {
        access.requireCompleted(userId, analysisId);
        notes.findByUserIdAndAnalysisId(userId, analysisId).ifPresent(notes::delete);
        return new NoteDto(generated(analysisId), false, null);
    }

    @Transactional(readOnly = true)
    public PdfFile exportPdf(Long userId, Long analysisId) {
        VideoAnalysis a = access.requireCompleted(userId, analysisId);
        String content = get(userId, analysisId).content();
        String title = a.getVideo().getTitle();
        byte[] bytes = pdf.render(title, "Study notes · Explainly · youtube.com/watch?v=" + a.getVideo().getYoutubeId(), content);
        String safe = title.replaceAll("[^A-Za-z0-9 _-]", "").trim().replaceAll("\\s+", "-");
        return new PdfFile((safe.isEmpty() ? "notes" : safe.substring(0, Math.min(60, safe.length()))) + "-notes.pdf", bytes);
    }

    private String generated(Long analysisId) {
        return explanations.findByAnalysisId(analysisId).map(e -> e.getGeneratedNotes() == null ? "" : e.getGeneratedNotes())
                .orElseThrow(() -> ApiException.notFound("Notes"));
    }
}
