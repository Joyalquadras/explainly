package com.explainly.auth;

import com.explainly.auth.AuthDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @Operation(summary = "Create an account (upgrades the current guest session if present)")
    public AuthResponse register(@Valid @RequestBody RegisterRequest req, @AuthenticationPrincipal Jwt jwt) {
        return authService.register(req, userId(jwt));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req);
    }

    @PostMapping("/guest")
    @Operation(summary = "Start a guest session with limited daily usage")
    public AuthResponse guest() {
        return authService.guest();
    }

    @PostMapping("/google")
    @Operation(summary = "Sign in with a Google ID token")
    public AuthResponse google(@Valid @RequestBody GoogleSignInRequest req, @AuthenticationPrincipal Jwt jwt) {
        return authService.google(req, userId(jwt));
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest req) {
        return authService.refresh(req);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest req) {
        authService.logout(req);
        return ResponseEntity.noContent().build();
    }

    private static Long userId(Jwt jwt) {
        return jwt == null ? null : Long.valueOf(jwt.getSubject());
    }
}
