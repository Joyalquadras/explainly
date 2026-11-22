package com.explainly.video;

import com.explainly.common.CurrentUser;
import com.explainly.video.AnalysisDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
@Tag(name = "Videos & analyses")
public class AnalysisController {

    private final VideoService videoService;
    private final AnalysisService analysisService;

    public AnalysisController(VideoService videoService, AnalysisService analysisService) {
        this.videoService = videoService;
        this.analysisService = analysisService;
    }

    @PostMapping("/videos/analyze")
    @Operation(summary = "Submit a YouTube URL for analysis (asynchronous)")
    public ResponseEntity<SubmitResponse> analyze(@Valid @RequestBody AnalyzeRequest req) {
        Long userId = CurrentUser.id();
        VideoService.SubmitResult result = videoService.submit(userId, req.url(), req.forceNew());
        StatusDto status = analysisService.status(userId, result.analysis().getId());
        return ResponseEntity.status(result.reused() ? HttpStatus.OK : HttpStatus.ACCEPTED)
                .body(new SubmitResponse(status, VideoDto.from(result.analysis().getVideo()), result.reused()));
    }

    @GetMapping("/analyses/{id}")
    @Operation(summary = "Full learning content for an analysis")
    public DetailDto detail(@PathVariable Long id) {
        return analysisService.detail(CurrentUser.id(), id);
    }

    @GetMapping("/analyses/{id}/status")
    @Operation(summary = "Lightweight processing status for polling")
    public StatusDto status(@PathVariable Long id) {
        return analysisService.status(CurrentUser.id(), id);
    }

    @GetMapping("/analyses/{id}/transcript")
    public TranscriptView transcript(@PathVariable Long id) {
        return analysisService.transcript(CurrentUser.id(), id);
    }

    @PostMapping("/analyses/{id}/transcript")
    @Operation(summary = "Provide transcript text when it could not be fetched automatically")
    public StatusDto submitTranscript(@PathVariable Long id, @Valid @RequestBody TranscriptTextRequest req) {
        return analysisService.submitTranscriptText(CurrentUser.id(), id, req.text());
    }

    @PostMapping(value = "/analyses/{id}/transcript/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a transcript or caption file (.txt, .srt, .vtt)")
    public StatusDto uploadTranscript(@PathVariable Long id, @RequestPart("file") MultipartFile file) {
        return analysisService.submitTranscriptFile(CurrentUser.id(), id, file);
    }

    @PostMapping(value = "/analyses/{id}/transcript/audio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload audio you have the right to use; it is transcribed with the configured speech-to-text provider")
    public StatusDto uploadAudio(@PathVariable Long id, @RequestPart("file") MultipartFile file) {
        return analysisService.submitAudio(CurrentUser.id(), id, file);
    }

    @PostMapping("/analyses/{id}/retry")
    public StatusDto retry(@PathVariable Long id) {
        return analysisService.retry(CurrentUser.id(), id);
    }
}
