package com.explainly.ai;

import com.explainly.config.AppProperties;
import com.explainly.transcript.TranscriptSegment;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Optional OpenAI image generation and speech-to-text (Whisper-compatible) integrations. */
@Component
public class MediaAiClient {

    private static final Logger log = LoggerFactory.getLogger(MediaAiClient.class);
    private final AppProperties.Ai ai;
    private final RestClient http;

    public MediaAiClient(AppProperties props, RestClient.Builder builder) {
        this.ai = props.ai();
        this.http = builder.build();
    }

    public boolean imagesEnabled() {
        return ai.images().enabled() && AppProperties.notBlank(ai.openai().apiKey());
    }

    public boolean transcriptionEnabled() {
        return ai.transcription().enabled() && AppProperties.notBlank(ai.openai().apiKey());
    }

    /** Returns base64 PNG data, or empty if generation fails (illustrations are best-effort). */
    public Optional<String> generateImage(String prompt) {
        if (!imagesEnabled()) return Optional.empty();
        try {
            JsonNode res = http.post().uri(base() + "/images/generations")
                    .header("Authorization", "Bearer " + ai.openai().apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("model", ai.images().model(), "size", "1024x1024", "n", 1,
                            "prompt", "Clean, friendly educational illustration with no text or letters. " + prompt))
                    .retrieve().body(JsonNode.class);
            String b64 = res == null ? null : res.path("data").path(0).path("b64_json").asText(null);
            return Optional.ofNullable(b64);
        } catch (Exception e) {
            log.warn("Image generation failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public List<TranscriptSegment> transcribe(byte[] audio, String filename) {
        if (!transcriptionEnabled()) throw new AiProviderException("Audio transcription is not configured on this server.");
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(audio) {
            @Override
            public String getFilename() { return filename; }
        });
        form.add("model", ai.transcription().model());
        form.add("response_format", "verbose_json");
        try {
            JsonNode res = http.post().uri(base() + "/audio/transcriptions")
                    .header("Authorization", "Bearer " + ai.openai().apiKey())
                    .contentType(MediaType.MULTIPART_FORM_DATA).body(form)
                    .retrieve().body(JsonNode.class);
            List<TranscriptSegment> segments = new ArrayList<>();
            if (res != null && res.path("segments").isArray()) {
                for (JsonNode s : res.path("segments")) {
                    segments.add(new TranscriptSegment(s.path("start").asDouble(), s.path("end").asDouble(), s.path("text").asText().trim()));
                }
            } else if (res != null && res.hasNonNull("text")) {
                segments.add(new TranscriptSegment(null, null, res.path("text").asText()));
            }
            return segments;
        } catch (Exception e) {
            log.warn("Audio transcription failed: {}", e.getMessage());
            throw new AiProviderException("We couldn't transcribe that audio file. Please try a different file.", e);
        }
    }

    private String base() {
        return ai.openai().baseUrl().replaceAll("/+$", "");
    }
}
