package com.explainly.auth;

import com.explainly.common.ApiException;
import com.explainly.common.Hashing;
import com.explainly.config.AppProperties;
import com.explainly.config.SecurityConfig;
import com.explainly.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Issues short-lived access JWTs and rotating, hashed refresh tokens; also signs OAuth state values. */
@Service
public class TokenService {

    private final JwtEncoder encoder;
    private final NimbusJwtDecoder stateDecoder;
    private final RefreshTokenRepository refreshTokens;
    private final AppProperties props;

    public TokenService(JwtEncoder encoder, SecretKey jwtSigningKey, RefreshTokenRepository refreshTokens, AppProperties props) {
        this.encoder = encoder;
        this.refreshTokens = refreshTokens;
        this.props = props;
        this.stateDecoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey).macAlgorithm(MacAlgorithm.HS256).build();
        this.stateDecoder.setJwtValidator(JwtValidators.createDefaultWithValidators(
                new JwtClaimValidator<String>("typ", "oauth_state"::equals)));
    }

    public record IssuedTokens(String accessToken, long expiresInSeconds, String refreshToken) {}

    @Transactional
    public IssuedTokens issue(User user) {
        Instant now = Instant.now();
        Duration ttl = props.security().accessTokenTtl();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(SecurityConfig.JwtService.ISSUER)
                .subject(String.valueOf(user.getId()))
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .claim("typ", "access")
                .claim("roles", List.of(user.getRole().name()))
                .build();
        String access = encode(claims);

        String rawRefresh = Hashing.randomToken(48);
        RefreshToken rt = new RefreshToken();
        rt.setUser(user);
        rt.setTokenHash(Hashing.sha256Hex(rawRefresh));
        rt.setCreatedAt(now);
        rt.setExpiresAt(now.plus(props.security().refreshTokenTtl()));
        refreshTokens.save(rt);
        return new IssuedTokens(access, ttl.toSeconds(), rawRefresh);
    }

    /** Validates and rotates a refresh token. Reuse of a revoked token revokes the whole family. */
    @Transactional(noRollbackFor = ApiException.class)
    public User consumeRefreshToken(String raw) {
        RefreshToken rt = refreshTokens.findByTokenHash(Hashing.sha256Hex(raw))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Your session has expired. Please sign in again."));
        if (rt.isRevoked()) {
            refreshTokens.revokeAllForUser(rt.getUser().getId());
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Your session has expired. Please sign in again.");
        }
        if (rt.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Your session has expired. Please sign in again.");
        }
        rt.setRevoked(true);
        return rt.getUser();
    }

    @Transactional
    public void revoke(String raw) {
        refreshTokens.findByTokenHash(Hashing.sha256Hex(raw)).ifPresent(t -> t.setRevoked(true));
    }

    public String signState(Map<String, Object> values, Duration ttl) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder b = JwtClaimsSet.builder().issuedAt(now).expiresAt(now.plus(ttl)).claim("typ", "oauth_state");
        values.forEach(b::claim);
        return encode(b.build());
    }

    public Map<String, Object> verifyState(String state) {
        try {
            return stateDecoder.decode(state).getClaims();
        } catch (JwtException e) {
            throw ApiException.badRequest("INVALID_STATE", "The sign-in link has expired. Please try again.");
        }
    }

    private String encode(JwtClaimsSet claims) {
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
