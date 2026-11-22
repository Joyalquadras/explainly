package com.explainly.common;

import com.explainly.config.AppProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * In-memory token-bucket rate limiter keyed by user (or client IP for anonymous calls).
 * Three tiers: authentication endpoints, AI-heavy endpoints and everything else.
 * For multi-instance deployments swap the bucket store for Redis.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Pattern AI_PATHS = Pattern.compile(
            "^/api/(videos/analyze|analyses/\\d+/(chat|quiz|flashcards|transcript.*|retry))$");

    private final AppProperties.RateLimit config;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimitFilter(AppProperties.RateLimit config) {
        this.config = config;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !config.enabled() || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        String tier;
        int perMinute;
        if (path.startsWith("/api/auth/") && "POST".equals(request.getMethod())) {
            tier = "auth";
            perMinute = config.authPerMinute();
        } else if ("POST".equals(request.getMethod()) && AI_PATHS.matcher(path).matches()) {
            tier = "ai";
            perMinute = config.aiPerMinute();
        } else {
            tier = "general";
            perMinute = config.generalPerMinute();
        }
        String key = tier + ":" + clientKey(request);
        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(perMinute));
        if (!bucket.tryConsume()) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", "60");
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write("{\"title\":\"Too Many Requests\",\"status\":429,\"code\":\"RATE_LIMITED\","
                    + "\"detail\":\"You're going a little fast. Please wait a moment and try again.\"}");
            return;
        }
        if (buckets.size() > 50_000) buckets.clear(); // crude memory bound
        chain.doFilter(request, response);
    }

    private String clientKey(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) return "u" + jwt.getSubject();
        return "ip" + request.getRemoteAddr();
    }

    static final class Bucket {
        private final double capacity;
        private final double refillPerNano;
        private double tokens;
        private long last;

        Bucket(int perMinute) {
            this.capacity = Math.max(1, perMinute);
            this.refillPerNano = capacity / 60_000_000_000d;
            this.tokens = capacity;
            this.last = System.nanoTime();
        }

        synchronized boolean tryConsume() {
            long now = System.nanoTime();
            tokens = Math.min(capacity, tokens + (now - last) * refillPerNano);
            last = now;
            if (tokens >= 1) {
                tokens -= 1;
                return true;
            }
            return false;
        }
    }
}
