package com.explainly.youtube;

import com.explainly.common.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/api/youtube")
@Tag(name = "YouTube")
public class YouTubeController {

    private final YouTubeOAuthService oauth;
    private final YouTubeMetadataClient metadata;

    public YouTubeController(YouTubeOAuthService oauth, YouTubeMetadataClient metadata) {
        this.oauth = oauth;
        this.metadata = metadata;
    }

    @GetMapping("/validate")
    @Operation(summary = "Validate a YouTube URL and preview its public metadata")
    public Map<String, Object> validate(@RequestParam String url) {
        YouTubeUrlParser.ParsedUrl parsed = YouTubeUrlParser.parse(url);
        VideoMetadata m = metadata.fetch(parsed.videoId());
        return Map.of("valid", true, "videoId", parsed.videoId(), "metadata", m);
    }

    @GetMapping("/connection")
    public YouTubeOAuthService.ConnectionStatus connection() {
        return oauth.status(CurrentUser.id());
    }

    @PostMapping("/connect")
    @Operation(summary = "Start the Google OAuth flow to connect a YouTube account")
    public Map<String, String> connect(@RequestBody(required = false) Map<String, String> body) {
        String returnTo = body == null ? null : body.get("returnTo");
        return Map.of("authorizationUrl", oauth.authorizationUrl(CurrentUser.id(), returnTo));
    }

    @GetMapping("/oauth/callback")
    public ResponseEntity<Void> callback(@RequestParam(required = false) String code,
                                         @RequestParam(required = false) String state,
                                         @RequestParam(required = false) String error) {
        String target = oauth.handleCallback(code, state == null ? "" : state, error);
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(target));
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }

    @DeleteMapping("/connection")
    @Operation(summary = "Disconnect YouTube and revoke the access grant at Google")
    public ResponseEntity<Void> disconnect() {
        oauth.disconnect(CurrentUser.id());
        return ResponseEntity.noContent().build();
    }
}
