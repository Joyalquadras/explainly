package com.explainly.youtube;

import com.explainly.auth.TokenService;
import com.explainly.common.ApiException;
import com.explainly.common.TokenCipher;
import com.explainly.config.AppProperties;
import com.explainly.user.Role;
import com.explainly.user.User;
import com.explainly.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Google OAuth 2.0 authorization-code flow for connecting a YouTube account.
 * Requests only {@code youtube.force-ssl}, the minimum scope that permits captions.list/captions.download.
 * Tokens are AES-GCM encrypted at rest; disconnecting revokes the grant at Google.
 */
@Service
public class YouTubeOAuthService {

    public static final String SCOPE = "https://www.googleapis.com/auth/youtube.force-ssl";
    private static final Logger log = LoggerFactory.getLogger(YouTubeOAuthService.class);

    private final AppProperties props;
    private final TokenService tokenService;
    private final TokenCipher cipher;
    private final ConnectedAccountRepository accounts;
    private final UserRepository users;
    private final RestClient http;

    public YouTubeOAuthService(AppProperties props, TokenService tokenService, TokenCipher cipher,
                               ConnectedAccountRepository accounts, UserRepository users, RestClient.Builder builder) {
        this.props = props;
        this.tokenService = tokenService;
        this.cipher = cipher;
        this.accounts = accounts;
        this.users = users;
        this.http = builder.build();
    }

    public record ConnectionStatus(boolean enabled, boolean connected, String accountName, String scopes, Instant connectedAt) {}

    public ConnectionStatus status(Long userId) {
        Optional<ConnectedAccount> acc = accounts.findByUserIdAndProvider(userId, ConnectedAccount.PROVIDER_YOUTUBE);
        return new ConnectionStatus(props.google().youtubeOAuthEnabled(), acc.isPresent(),
                acc.map(ConnectedAccount::getAccountName).orElse(null),
                acc.map(ConnectedAccount::getScopes).orElse(null),
                acc.map(ConnectedAccount::getCreatedAt).orElse(null));
    }

    public String authorizationUrl(Long userId, String returnTo) {
        requireEnabled();
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        if (user.getRole() == Role.GUEST) {
            throw ApiException.forbidden("Create a free account to connect YouTube.");
        }
        String target = sanitizeReturnTo(returnTo);
        String state = tokenService.signState(Map.of("uid", String.valueOf(userId), "ret", target), Duration.ofMinutes(10));
        return UriComponentsBuilder.fromUriString("https://accounts.google.com/o/oauth2/v2/auth")
                .queryParam("client_id", props.google().oauthClientId())
                .queryParam("redirect_uri", props.google().oauthRedirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", SCOPE)
                .queryParam("access_type", "offline")
                .queryParam("prompt", "consent")
                .queryParam("include_granted_scopes", "true")
                .queryParam("state", state)
                .encode().build().toUriString();
    }

    /** Handles Google's redirect. Returns the URL to send the browser to afterwards. */
    @Transactional
    public String handleCallback(String code, String state, String error) {
        Map<String, Object> claims;
        try {
            claims = tokenService.verifyState(state);
        } catch (ApiException e) {
            return defaultReturn() + "?youtube=error&reason=expired";
        }
        String ret = String.valueOf(claims.getOrDefault("ret", defaultReturn()));
        if (error != null || code == null) {
            return withParams(ret, "youtube=error&reason=" + (error == null ? "cancelled" : "denied"));
        }
        Long userId = Long.valueOf(String.valueOf(claims.get("uid")));
        try {
            JsonNode token = exchange(Map.of("code", code, "grant_type", "authorization_code",
                    "redirect_uri", props.google().oauthRedirectUri()));
            String accessToken = token.path("access_token").asText();
            String refreshToken = token.path("refresh_token").asText(null);
            String scopes = token.path("scope").asText(SCOPE);
            if (!scopes.contains(SCOPE)) {
                return withParams(ret, "youtube=error&reason=scope");
            }
            User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
            ConnectedAccount acc = accounts.findByUserIdAndProvider(userId, ConnectedAccount.PROVIDER_YOUTUBE)
                    .orElseGet(ConnectedAccount::new);
            acc.setUser(user);
            acc.setProvider(ConnectedAccount.PROVIDER_YOUTUBE);
            acc.setAccessTokenEnc(cipher.encrypt(accessToken));
            if (refreshToken != null) acc.setRefreshTokenEnc(cipher.encrypt(refreshToken));
            acc.setTokenExpiresAt(Instant.now().plusSeconds(token.path("expires_in").asLong(3600) - 60));
            acc.setScopes(scopes);
            fetchChannel(accessToken).ifPresent(ch -> {
                acc.setProviderAccountId(ch[0]);
                acc.setAccountName(ch[1]);
            });
            accounts.save(acc);
            return withParams(ret, "youtube=connected");
        } catch (Exception e) {
            log.warn("YouTube OAuth callback failed for user {}: {}", userId, e.getMessage());
            return withParams(ret, "youtube=error&reason=exchange");
        }
    }

    /** Returns a usable access token for the user's connected account, refreshing it when needed. */
    @Transactional
    public Optional<String> accessToken(Long userId) {
        if (!props.google().youtubeOAuthEnabled() || userId == null) return Optional.empty();
        Optional<ConnectedAccount> maybe = accounts.findByUserIdAndProvider(userId, ConnectedAccount.PROVIDER_YOUTUBE);
        if (maybe.isEmpty()) return Optional.empty();
        ConnectedAccount acc = maybe.get();
        if (acc.getTokenExpiresAt() != null && acc.getTokenExpiresAt().isAfter(Instant.now())) {
            return Optional.of(cipher.decrypt(acc.getAccessTokenEnc()));
        }
        if (acc.getRefreshTokenEnc() == null) return Optional.empty();
        try {
            JsonNode token = exchange(Map.of("grant_type", "refresh_token",
                    "refresh_token", cipher.decrypt(acc.getRefreshTokenEnc())));
            String access = token.path("access_token").asText();
            acc.setAccessTokenEnc(cipher.encrypt(access));
            acc.setTokenExpiresAt(Instant.now().plusSeconds(token.path("expires_in").asLong(3600) - 60));
            accounts.save(acc);
            return Optional.of(access);
        } catch (Exception e) {
            log.warn("Could not refresh YouTube token for user {}: {}", userId, e.getMessage());
            return Optional.empty();
        }
    }

    @Transactional
    public void disconnect(Long userId) {
        accounts.findByUserIdAndProvider(userId, ConnectedAccount.PROVIDER_YOUTUBE).ifPresent(acc -> {
            String token = acc.getRefreshTokenEnc() != null ? cipher.decrypt(acc.getRefreshTokenEnc())
                    : cipher.decrypt(acc.getAccessTokenEnc());
            try {
                MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
                form.add("token", token);
                http.post().uri("https://oauth2.googleapis.com/revoke")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().toBodilessEntity();
            } catch (Exception e) {
                // The grant may already be revoked by the user in their Google account; still delete locally.
                log.info("Google token revoke returned an error for user {}: {}", userId, e.getMessage());
            }
            accounts.delete(acc);
        });
    }

    private JsonNode exchange(Map<String, String> params) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        params.forEach(form::add);
        form.add("client_id", props.google().oauthClientId());
        form.add("client_secret", props.google().oauthClientSecret());
        return http.post().uri("https://oauth2.googleapis.com/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(JsonNode.class);
    }

    private Optional<String[]> fetchChannel(String accessToken) {
        try {
            JsonNode body = http.get().uri("https://www.googleapis.com/youtube/v3/channels?part=snippet&mine=true")
                    .header("Authorization", "Bearer " + accessToken).retrieve().body(JsonNode.class);
            JsonNode item = body == null ? null : body.path("items").path(0);
            if (item == null || item.isMissingNode()) return Optional.empty();
            return Optional.of(new String[]{item.path("id").asText(), item.path("snippet").path("title").asText()});
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private void requireEnabled() {
        if (!props.google().youtubeOAuthEnabled()) {
            throw ApiException.serviceUnavailable("YOUTUBE_OAUTH_NOT_CONFIGURED",
                    "Connecting YouTube is not configured on this server.");
        }
    }

    private String sanitizeReturnTo(String returnTo) {
        if (returnTo == null || returnTo.isBlank()) return defaultReturn();
        for (String prefix : props.security().redirectPrefixes()) {
            if (returnTo.startsWith(prefix)) return returnTo;
        }
        throw ApiException.badRequest("INVALID_RETURN_URL", "That return address is not allowed.");
    }

    private String defaultReturn() {
        return props.webAppUrl() + "/settings";
    }

    private static String withParams(String url, String params) {
        return url + (url.contains("?") ? "&" : "?") + params;
    }
}
