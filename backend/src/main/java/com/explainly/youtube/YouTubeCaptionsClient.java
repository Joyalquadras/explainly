package com.explainly.youtube;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

/**
 * Official YouTube Data API captions access. captions.download only succeeds when the authorised
 * account is permitted to manage the video's captions (typically the channel owner).
 */
@Component
public class YouTubeCaptionsClient {

    private final RestClient http;

    public YouTubeCaptionsClient(RestClient.Builder builder) {
        this.http = builder.build();
    }

    public record CaptionTrack(String id, String language, String trackKind) {}

    public sealed interface Result permits Downloaded, NotAvailable {}
    public record Downloaded(String vtt, String language) implements Result {}
    public record NotAvailable(String reason) implements Result {}

    public Result download(String videoId, String accessToken) {
        List<CaptionTrack> tracks;
        try {
            JsonNode body = http.get().uri("https://www.googleapis.com/youtube/v3/captions?part=snippet&videoId={id}", videoId)
                    .header("Authorization", "Bearer " + accessToken).retrieve().body(JsonNode.class);
            tracks = body == null ? List.of() : StreamSupport.stream(body.path("items").spliterator(), false)
                    .map(i -> new CaptionTrack(i.path("id").asText(), i.path("snippet").path("language").asText(""),
                            i.path("snippet").path("trackKind").asText("")))
                    .toList();
        } catch (HttpClientErrorException e) {
            return new NotAvailable("YouTube did not allow listing captions for this video with your account.");
        }
        Optional<CaptionTrack> best = tracks.stream().min(Comparator
                .comparing((CaptionTrack t) -> t.trackKind().equalsIgnoreCase("asr") ? 1 : 0)
                .thenComparing(t -> t.language().startsWith("en") ? 0 : 1));
        if (best.isEmpty()) return new NotAvailable("This video has no caption tracks.");
        try {
            String vtt = http.get().uri("https://www.googleapis.com/youtube/v3/captions/{id}?tfmt=vtt", best.get().id())
                    .header("Authorization", "Bearer " + accessToken).retrieve().body(String.class);
            if (vtt == null || vtt.isBlank()) return new NotAvailable("The caption track was empty.");
            return new Downloaded(vtt, best.get().language());
        } catch (HttpClientErrorException e) {
            return new NotAvailable("YouTube only allows downloading captions for videos your connected account manages.");
        }
    }
}
