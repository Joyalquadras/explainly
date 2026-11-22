package com.explainly.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

@ConfigurationProperties(prefix = "explainly")
public record AppProperties(
        String webAppUrl,
        Security security,
        RateLimit rateLimit,
        Limits limits,
        Google google,
        YouTube youtube,
        Ai ai) {

    public record Security(String jwtSecret, Duration accessTokenTtl, Duration refreshTokenTtl,
                           String tokenEncryptionKey, String corsAllowedOrigins, String allowedRedirectPrefixes) {
        public List<String> corsOrigins() { return split(corsAllowedOrigins); }
        public List<String> redirectPrefixes() { return split(allowedRedirectPrefixes); }
    }

    public record RateLimit(boolean enabled, int generalPerMinute, int aiPerMinute, int authPerMinute) {}

    public record Limits(int analysesPerDayUser, int analysesPerDayGuest, int maxTranscriptChars,
                         long maxTranscriptUploadBytes, long maxAudioUploadBytes) {}

    public record Google(String clientIds, String oauthClientId, String oauthClientSecret, String oauthRedirectUri) {
        public List<String> clientIdList() { return split(clientIds); }
        public boolean signInEnabled() { return !clientIdList().isEmpty(); }
        public boolean youtubeOAuthEnabled() { return notBlank(oauthClientId) && notBlank(oauthClientSecret); }
    }

    public record YouTube(String apiKey) {
        public boolean hasApiKey() { return notBlank(apiKey); }
    }

    public record Ai(String provider, int maxDirectTranscriptChars, Anthropic anthropic, OpenAi openai,
                     Images images, Transcription transcription) {
        public record Anthropic(String apiKey, String baseUrl, String model, String effort, boolean serverFallbacks) {}
        public record OpenAi(String apiKey, String baseUrl, String model, boolean jsonMode) {}
        public record Images(boolean enabled, String model, int maxPerVideo) {}
        public record Transcription(boolean enabled, String model) {}
    }

    static List<String> split(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    public static boolean notBlank(String s) { return s != null && !s.isBlank(); }
}
