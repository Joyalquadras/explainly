package com.explainly.youtube;

import com.explainly.common.ApiException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Strict YouTube URL validation. Only well-known YouTube hosts over http(s) are accepted, and only the
 * 11-character video id is extracted. The server never requests the user-supplied URL itself, which
 * removes SSRF risk entirely: every outbound call is built from the validated id.
 */
public final class YouTubeUrlParser {

    private static final Pattern VIDEO_ID = Pattern.compile("^[A-Za-z0-9_-]{11}$");
    private static final Set<String> WATCH_HOSTS = Set.of("youtube.com", "www.youtube.com", "m.youtube.com",
            "music.youtube.com", "youtube-nocookie.com", "www.youtube-nocookie.com");
    private static final Set<String> SHORT_HOSTS = Set.of("youtu.be", "www.youtu.be");

    private YouTubeUrlParser() {}

    public record ParsedUrl(String videoId, Integer startSeconds) {}

    public static ParsedUrl parse(String input) {
        return tryParse(input).orElseThrow(() -> ApiException.badRequest("INVALID_YOUTUBE_URL",
                "That doesn't look like a YouTube video link. Try something like https://www.youtube.com/watch?v=..."));
    }

    public static Optional<ParsedUrl> tryParse(String input) {
        if (input == null) return Optional.empty();
        String value = input.trim();
        if (value.length() > 2048 || value.isEmpty()) return Optional.empty();
        if (VIDEO_ID.matcher(value).matches()) return Optional.of(new ParsedUrl(value, null));
        if (!value.matches("(?i)^https?://.*")) value = "https://" + value;
        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!scheme.equals("https") && !scheme.equals("http")) return Optional.empty();
        if (uri.getUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443 && uri.getPort() != 80)) {
            return Optional.empty();
        }
        String path = uri.getPath() == null ? "" : uri.getPath();
        String id = null;
        if (SHORT_HOSTS.contains(host)) {
            id = firstSegment(path);
        } else if (WATCH_HOSTS.contains(host)) {
            if (path.equals("/watch") || path.equals("/watch/")) {
                id = queryParam(uri.getRawQuery(), "v");
            } else {
                for (String prefix : new String[]{"/embed/", "/shorts/", "/live/", "/v/", "/e/"}) {
                    if (path.startsWith(prefix)) {
                        id = firstSegment(path.substring(prefix.length() - 1));
                        break;
                    }
                }
            }
        }
        if (id == null || !VIDEO_ID.matcher(id).matches()) return Optional.empty();
        return Optional.of(new ParsedUrl(id, parseStart(queryParam(uri.getRawQuery(), "t"))));
    }

    private static String firstSegment(String path) {
        String p = path.startsWith("/") ? path.substring(1) : path;
        int slash = p.indexOf('/');
        return slash >= 0 ? p.substring(0, slash) : p;
    }

    private static String queryParam(String rawQuery, String name) {
        if (rawQuery == null) return null;
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(name)) return pair.substring(eq + 1);
        }
        return null;
    }

    private static Integer parseStart(String t) {
        if (t == null || t.isBlank()) return null;
        try {
            if (t.matches("\\d+s?")) return Integer.parseInt(t.replace("s", ""));
            var m = Pattern.compile("(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+)s)?").matcher(t);
            if (m.matches()) {
                int h = m.group(1) == null ? 0 : Integer.parseInt(m.group(1));
                int min = m.group(2) == null ? 0 : Integer.parseInt(m.group(2));
                int s = m.group(3) == null ? 0 : Integer.parseInt(m.group(3));
                return h * 3600 + min * 60 + s;
            }
        } catch (NumberFormatException ignored) {
            // fall through
        }
        return null;
    }
}
