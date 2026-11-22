package com.explainly.youtube;

import com.explainly.common.ApiException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class YouTubeUrlParserTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "http://youtube.com/watch?feature=share&v=dQw4w9WgXcQ",
            "https://m.youtube.com/watch?v=dQw4w9WgXcQ&list=PL123",
            "youtube.com/watch?v=dQw4w9WgXcQ",
            "https://youtu.be/dQw4w9WgXcQ?si=abc",
            "https://www.youtube.com/shorts/dQw4w9WgXcQ",
            "https://www.youtube.com/embed/dQw4w9WgXcQ",
            "https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ",
            "https://www.youtube.com/live/dQw4w9WgXcQ",
            "dQw4w9WgXcQ",
            "  https://music.youtube.com/watch?v=dQw4w9WgXcQ  "})
    void acceptsYouTubeUrls(String url) {
        assertThat(YouTubeUrlParser.parse(url).videoId()).isEqualTo("dQw4w9WgXcQ");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://evil.com/watch?v=dQw4w9WgXcQ",
            "https://youtube.com.evil.com/watch?v=dQw4w9WgXcQ",
            "https://www.youtube.com@evil.com/watch?v=dQw4w9WgXcQ",
            "https://user:pass@www.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://www.youtube.com:8443/watch?v=dQw4w9WgXcQ",
            "javascript:alert(1)//youtube.com/watch?v=dQw4w9WgXcQ",
            "file:///etc/passwd",
            "https://www.youtube.com/watch?v=short",
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ%2F..",
            "https://127.0.0.1/watch?v=dQw4w9WgXcQ",
            "https://www.youtube.com/channel/UC123",
            ""})
    void rejectsEverythingElse(String url) {
        assertThat(YouTubeUrlParser.tryParse(url)).isEmpty();
    }

    @Test
    void parsesStartTime() {
        assertThat(YouTubeUrlParser.parse("https://youtu.be/dQw4w9WgXcQ?t=90").startSeconds()).isEqualTo(90);
        assertThat(YouTubeUrlParser.parse("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=1m5s").startSeconds()).isEqualTo(65);
    }

    @Test
    void parseThrowsFriendlyError() {
        assertThatThrownBy(() -> YouTubeUrlParser.parse("https://vimeo.com/123"))
                .isInstanceOf(ApiException.class).hasMessageContaining("YouTube");
    }
}
