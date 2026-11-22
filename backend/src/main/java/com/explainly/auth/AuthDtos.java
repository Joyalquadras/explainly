package com.explainly.auth;

import com.explainly.user.UserDto;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {}

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(min = 8, max = 128, message = "Password must be 8-128 characters") String password,
            @NotBlank @Size(max = 120) String displayName) {}

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

    public record GoogleSignInRequest(@NotBlank @Size(max = 8192) String idToken) {}

    public record RefreshRequest(@NotBlank @Size(max = 512) String refreshToken) {}

    public record AuthResponse(String accessToken, long expiresIn, String refreshToken, UserDto user) {}

    public record PublicConfig(boolean googleSignInEnabled, String googleWebClientId, boolean youtubeConnectEnabled,
                               boolean aiConfigured, String aiProvider, boolean audioTranscriptionEnabled,
                               boolean imageGenerationEnabled, long maxTranscriptUploadBytes, long maxAudioUploadBytes) {}
}
