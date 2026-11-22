package com.explainly.auth;

import com.explainly.common.ApiException;
import com.explainly.config.AppProperties;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Verifies Google ID tokens (from Google Identity Services on web and expo-auth-session on mobile)
 * against Google's published JWKS. Checks signature, expiry, issuer and audience.
 */
@Component
public class GoogleIdTokenVerifier {

    public record GoogleProfile(String subject, String email, boolean emailVerified, String name, String picture) {}

    private static final Set<String> ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");
    private final AppProperties props;
    private volatile JwtDecoder decoder;

    public GoogleIdTokenVerifier(AppProperties props) {
        this.props = props;
    }

    public GoogleProfile verify(String idToken) {
        if (!props.google().signInEnabled()) {
            throw ApiException.serviceUnavailable("GOOGLE_NOT_CONFIGURED", "Google sign-in is not configured on this server.");
        }
        try {
            Jwt jwt = decoder().decode(idToken);
            Object verified = jwt.getClaims().get("email_verified");
            return new GoogleProfile(jwt.getSubject(), jwt.getClaimAsString("email"),
                    Boolean.TRUE.equals(verified) || "true".equals(String.valueOf(verified)),
                    jwt.getClaimAsString("name"), jwt.getClaimAsString("picture"));
        } catch (JwtException e) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_GOOGLE_TOKEN", "Google sign-in failed. Please try again.");
        }
    }

    private JwtDecoder decoder() {
        if (decoder == null) {
            synchronized (this) {
                if (decoder == null) {
                    NimbusJwtDecoder d = NimbusJwtDecoder.withJwkSetUri("https://www.googleapis.com/oauth2/v3/certs").build();
                    List<String> audiences = props.google().clientIdList();
                    d.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                            JwtValidators.createDefault(),
                            jwt -> ISSUERS.contains(String.valueOf(jwt.getClaims().get("iss")))
                                    ? OAuth2TokenValidatorResult.success()
                                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "bad issuer", null)),
                            jwt -> jwt.getAudience() != null && jwt.getAudience().stream().anyMatch(audiences::contains)
                                    ? OAuth2TokenValidatorResult.success()
                                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "bad audience", null))));
                    decoder = d;
                }
            }
        }
        return decoder;
    }
}
