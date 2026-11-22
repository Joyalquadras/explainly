package com.explainly.notes;

import com.explainly.common.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/analyses/{analysisId}/notes")
@Tag(name = "Notes")
public class NotesController {

    private final NotesService notes;

    public NotesController(NotesService notes) {
        this.notes = notes;
    }

    public record SaveNoteRequest(@NotNull @Size(max = 200_000) String content) {}

    @GetMapping
    public NotesService.NoteDto get(@PathVariable Long analysisId) {
        return notes.get(CurrentUser.id(), analysisId);
    }

    @PutMapping
    public NotesService.NoteDto save(@PathVariable Long analysisId, @Valid @RequestBody SaveNoteRequest req) {
        return notes.save(CurrentUser.id(), analysisId, req.content());
    }

    @PostMapping("/reset")
    public NotesService.NoteDto reset(@PathVariable Long analysisId) {
        return notes.reset(CurrentUser.id(), analysisId);
    }

    @GetMapping(value = "/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@PathVariable Long analysisId) {
        NotesService.PdfFile file = notes.exportPdf(CurrentUser.id(), analysisId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.filename()).build().toString())
                .contentType(MediaType.APPLICATION_PDF).body(file.bytes());
    }
}
