package com.explainly.youtube;

import com.explainly.common.ApiException;
import com.explainly.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;

/**
 * Retrieves public video metadata through official interfaces: the YouTube Data API v3 (when an API key
 * is configured) and otherwise YouTube's public oEmbed endpoint.
 */
@Component
public class YouTubeMetadataClient {

    private static final Logger log = LoggerFactory.getLogger(YouTubeMetadataClient.class);
    private final RestClient http;
    private final AppProperties props;

    public YouTubeMetadataClient(RestClient.Builder builder, AppProperties props) {
        this.http = builder.build();
        this.props = props;
    }

    public VideoMetadata fetch(String videoId) {
        if (props.youtube().hasApiKey()) {
            try {
                VideoMetadata viaApi = fetchFromDataApi(videoId);
                if (viaApi != null) return viaApi;
                throw ApiException.unprocessable("VIDEO_UNAVAILABLE", "This video is private, removed, or does not exist.");
            } catch (ApiException e) {
                throw e;
            } catch (Exception e) {
                log.warn("YouTube Data API lookup failed for {}: {}", videoId, e.getMessage());
            }
        }
        return fetchFromOEmbed(videoId);
    }

    private VideoMetadata fetchFromDataApi(String videoId) {
        JsonNode body = http.get()
                .uri("https://www.googleapis.com/youtube/v3/videos?part=snippet,contentDetails&id={id}&key={key}",
                        videoId, props.youtube().apiKey())
                .retrieve().body(JsonNode.class);
        if (body == null || !body.path("items").isArray() || body.path("items").isEmpty()) return null;
        JsonNode item = body.path("items").get(0);
        JsonNode snippet = item.path("snippet");
        JsonNode thumbs = snippet.path("thumbnails");
        String thumb = firstText(thumbs.path("maxres").path("url"), thumbs.path("high").path("url"),
                thumbs.path("medium").path("url"), thumbs.path("default").path("url"));
        Integer duration = null;
        try {
            String iso = item.path("contentDetails").path("duration").asText(null);
            if (iso != null) duration = (int) Duration.parse(iso).toSeconds();
        } catch (Exception ignored) {
            // live streams report P0D or omit duration
        }
        Instant published = null;
        try {
            published = Instant.parse(snippet.path("publishedAt").asText());
        } catch (Exception ignored) {
            // optional
        }
        boolean captions = "true".equals(item.path("contentDetails").path("caption").asText());
        return new VideoMetadata(videoId, snippet.path("title").asText("Untitled video"),
                snippet.path("channelTitle").asText(null), truncate(snippet.path("description").asText(null), 5000),
                thumb, duration, published, captions);
    }

    private VideoMetadata fetchFromOEmbed(String videoId) {
        try {
            JsonNode body = http.get()
                    .uri("https://www.youtube.com/oembed?format=json&url=https://www.youtube.com/watch?v={id}", videoId)
                    .retrieve().body(JsonNode.class);
            if (body == null) throw new IllegalStateException("empty oEmbed response");
            return new VideoMetadata(videoId, body.path("title").asText("YouTube video"),
                    body.path("author_name").asText(null), null,
                    "https://i.ytimg.com/vi/" + videoId + "/hqdefault.jpg", null, null, false);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403 || e.getStatusCode().value() == 404
                    || e.getStatusCode().value() == 400) {
                throw ApiException.unprocessable("VIDEO_UNAVAILABLE",
                        "This video is private, removed, or does not allow embedding, so it can't be analyzed.");
            }
            log.warn("oEmbed lookup failed for {}: {}", videoId, e.getMessage());
        } catch (Exception e) {
            log.warn("oEmbed lookup failed for {}: {}", videoId, e.getMessage());
        }
        // YouTube unreachable: continue with minimal metadata rather than blocking the learner.
        return new VideoMetadata(videoId, "YouTube video " + videoId, null, null,
                "https://i.ytimg.com/vi/" + videoId + "/hqdefault.jpg", null, null, false);
    }

    private static String firstText(JsonNode... nodes) {
        for (JsonNode n : nodes) if (n != null && n.isTextual() && !n.asText().isBlank()) return n.asText();
        return null;
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
