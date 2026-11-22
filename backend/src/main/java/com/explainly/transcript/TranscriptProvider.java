package com.explainly.transcript;

import com.explainly.video.Video;

import java.util.List;
import java.util.Optional;

/**
 * A permitted source of transcripts that can be tried automatically for a video.
 * Implementations must only use official, authorised interfaces.
 */
public interface TranscriptProvider {

    record FetchedTranscript(TranscriptSource source, String language, List<TranscriptSegment> segments) {}

    record Attempt(Optional<FetchedTranscript> transcript, String unavailableReason) {
        public static Attempt found(FetchedTranscript t) { return new Attempt(Optional.of(t), null); }
        public static Attempt unavailable(String reason) { return new Attempt(Optional.empty(), reason); }
    }

    String name();

    Attempt fetch(Video video, Long requestingUserId);
}
