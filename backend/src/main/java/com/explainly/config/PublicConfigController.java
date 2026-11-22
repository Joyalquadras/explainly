package com.explainly.config;

import com.explainly.ai.AiService;
import com.explainly.ai.MediaAiClient;
import com.explainly.auth.AuthDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Tells clients which optional features are configured, so they never show controls that cannot work. */
@RestController
@Tag(name = "Authentication")
public class PublicConfigController {

    private final AppProperties props;
    private final AiService ai;
    private final MediaAiClient media;

    public PublicConfigController(AppProperties props, AiService ai, MediaAiClient media) {
        this.props = props;
        this.ai = ai;
        this.media = media;
    }

    @GetMapping("/api/auth/config")
    @Operation(summary = "Public feature flags for clients")
    public AuthDtos.PublicConfig config() {
        var google = props.google();
        return new AuthDtos.PublicConfig(google.signInEnabled(),
                google.clientIdList().isEmpty() ? null : google.clientIdList().getFirst(),
                google.youtubeOAuthEnabled(), ai.isConfigured(),
                ai.activeClient().map(c -> c.id()).orElse(null),
                media.transcriptionEnabled(), media.imagesEnabled(),
                props.limits().maxTranscriptUploadBytes(), props.limits().maxAudioUploadBytes());
    }
}
