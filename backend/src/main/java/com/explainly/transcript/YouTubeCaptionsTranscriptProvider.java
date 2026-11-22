package com.explainly.transcript;

import com.explainly.video.Video;
import com.explainly.youtube.YouTubeCaptionsClient;
import com.explainly.youtube.YouTubeOAuthService;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Downloads captions with the requesting user's connected YouTube account (official Data API). */
@Component
@Order(10)
public class YouTubeCaptionsTranscriptProvider implements TranscriptProvider {

    private final YouTubeOAuthService oauth;
    private final YouTubeCaptionsClient captions;

    public YouTubeCaptionsTranscriptProvider(YouTubeOAuthService oauth, YouTubeCaptionsClient captions) {
        this.oauth = oauth;
        this.captions = captions;
    }

    @Override
    public String name() { return "YouTube captions (connected account)"; }

    @Override
    public Attempt fetch(Video video, Long requestingUserId) {
        Optional<String> token = oauth.accessToken(requestingUserId);
        if (token.isEmpty()) {
            return Attempt.unavailable("No YouTube account is connected, so captions can't be downloaded automatically.");
        }
        return switch (captions.download(video.getYoutubeId(), token.get())) {
            case YouTubeCaptionsClient.Downloaded d -> {
                var segments = TranscriptParser.parse(d.vtt());
                yield segments.isEmpty() ? Attempt.unavailable("The caption track was empty.")
                        : Attempt.found(new FetchedTranscript(TranscriptSource.YOUTUBE_CAPTIONS, d.language(), segments));
            }
            case YouTubeCaptionsClient.NotAvailable n -> Attempt.unavailable(n.reason());
        };
    }
}
