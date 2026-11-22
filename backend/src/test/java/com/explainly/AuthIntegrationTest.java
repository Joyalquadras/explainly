package com.explainly;

import com.explainly.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthIntegrationTest extends IntegrationTest {

    @Test
    void registerLoginRefreshRotationAndReuseDetection() throws Exception {
        JsonNode reg = json(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", "Ada@Example.com", "password", "correct horse", "displayName", "Ada"))))
                .andExpect(status().isOk()).andReturn());
        assertThat(reg.path("user").path("email").asText()).isEqualTo("ada@example.com");
        assertThat(reg.path("user").path("role").asText()).isEqualTo("USER");

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", "ada@example.com", "password", "another pass", "displayName", "Ada"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", "ada@example.com", "password", "wrong password"))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        JsonNode login = json(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("email", "ada@example.com", "password", "correct horse")))).andExpect(status().isOk()).andReturn());
        String refresh = login.path("refreshToken").asText();

        JsonNode rotated = json(mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("refreshToken", refresh)))).andExpect(status().isOk()).andReturn());
        String newRefresh = rotated.path("refreshToken").asText();
        assertThat(newRefresh).isNotEqualTo(refresh);

        // Re-using the old token is treated as theft: it fails and revokes the whole family.
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(body(Map.of("refreshToken", refresh))))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(body(Map.of("refreshToken", newRefresh))))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + rotated.path("accessToken").asText()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.displayName").value("Ada"));
    }

    @Test
    void guestCanUpgradeAndKeepsIdentity() throws Exception {
        JsonNode guest = json(mvc.perform(post("/api/auth/guest")).andExpect(status().isOk()).andReturn());
        long guestId = guest.path("user").path("id").asLong();
        assertThat(guest.path("user").path("role").asText()).isEqualTo("GUEST");
        JsonNode upgraded = json(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + guest.path("accessToken").asText())
                        .content(body(Map.of("email", "grace@example.com", "password", "password123", "displayName", "Grace"))))
                .andExpect(status().isOk()).andReturn());
        assertThat(upgraded.path("user").path("id").asLong()).isEqualTo(guestId);
        assertThat(upgraded.path("user").path("role").asText()).isEqualTo("USER");
    }

    @Test
    void validationAndAuthErrorsAreProblemDetails() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", "not-an-email", "password", "short", "displayName", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.password").exists());
        mvc.perform(get("/api/history")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(get("/api/history").header("Authorization", "Bearer forged.token.value")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/stats").header("Authorization", "Bearer " + guestToken())).andExpect(status().isForbidden());
    }

    @Test
    void publicConfigReportsFeatures() throws Exception {
        mvc.perform(get("/api/auth/config")).andExpect(status().isOk())
                .andExpect(jsonPath("$.aiConfigured").value(true))
                .andExpect(jsonPath("$.googleSignInEnabled").value(false))
                .andExpect(jsonPath("$.youtubeConnectEnabled").value(false));
    }

    @Test
    void googleAndYouTubeReportNotConfigured() throws Exception {
        mvc.perform(post("/api/auth/google").contentType(MediaType.APPLICATION_JSON).content(body(Map.of("idToken", "x"))))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("GOOGLE_NOT_CONFIGURED"));
        String token = guestToken();
        mvc.perform(authed(get("/api/youtube/connection"), token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false)).andExpect(jsonPath("$.connected").value(false));
        mvc.perform(authed(post("/api/youtube/connect"), token)).andExpect(status().isServiceUnavailable());
    }
}
