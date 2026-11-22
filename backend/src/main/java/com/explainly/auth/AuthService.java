package com.explainly.auth;

import com.explainly.auth.AuthDtos.*;
import com.explainly.common.ApiException;
import com.explainly.user.AuthProvider;
import com.explainly.user.Role;
import com.explainly.user.User;
import com.explainly.user.UserDto;
import com.explainly.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;
    private final GoogleIdTokenVerifier googleVerifier;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, TokenService tokens,
                       GoogleIdTokenVerifier googleVerifier) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.googleVerifier = googleVerifier;
    }

    /** Registers a new account. If the caller is a guest, the guest account is upgraded and keeps its history. */
    @Transactional
    public AuthResponse register(RegisterRequest req, Long currentUserId) {
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        if (users.findByEmailIgnoreCase(email).isPresent()) {
            throw ApiException.conflict("EMAIL_TAKEN", "An account with this email already exists. Try signing in.");
        }
        User user = guest(currentUserId).orElseGet(User::new);
        user.setEmail(email);
        user.setDisplayName(req.displayName().trim());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setRole(Role.USER);
        user.setAuthProvider(AuthProvider.LOCAL);
        user.setLastLoginAt(Instant.now());
        return respond(users.save(user));
    }

    @Transactional
    public AuthResponse login(LoginRequest req) {
        User user = users.findByEmailIgnoreCase(req.email().trim())
                .filter(u -> u.getPasswordHash() != null && passwordEncoder.matches(req.password(), u.getPasswordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email or password is incorrect."));
        user.setLastLoginAt(Instant.now());
        return respond(user);
    }

    @Transactional
    public AuthResponse guest() {
        User user = new User();
        user.setDisplayName("Guest learner");
        user.setRole(Role.GUEST);
        user.setAuthProvider(AuthProvider.GUEST);
        user.setLastLoginAt(Instant.now());
        return respond(users.save(user));
    }

    @Transactional
    public AuthResponse google(GoogleSignInRequest req, Long currentUserId) {
        GoogleIdTokenVerifier.GoogleProfile profile = googleVerifier.verify(req.idToken());
        User user = users.findByGoogleSubject(profile.subject())
                .or(() -> profile.emailVerified() && profile.email() != null
                        ? users.findByEmailIgnoreCase(profile.email()) : Optional.empty())
                .or(() -> guest(currentUserId))
                .orElseGet(User::new);
        user.setGoogleSubject(profile.subject());
        if (user.getEmail() == null && profile.emailVerified()) user.setEmail(profile.email().toLowerCase(Locale.ROOT));
        if (user.getDisplayName() == null || user.getRole() == Role.GUEST) {
            user.setDisplayName(profile.name() != null ? profile.name() : "Learner");
        }
        if (profile.picture() != null) user.setAvatarUrl(profile.picture());
        if (user.getRole() == null || user.getRole() == Role.GUEST) user.setRole(Role.USER);
        if (user.getAuthProvider() == null || user.getAuthProvider() == AuthProvider.GUEST) user.setAuthProvider(AuthProvider.GOOGLE);
        user.setLastLoginAt(Instant.now());
        return respond(users.save(user));
    }

    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse refresh(RefreshRequest req) {
        return respond(tokens.consumeRefreshToken(req.refreshToken()));
    }

    @Transactional
    public void logout(RefreshRequest req) {
        tokens.revoke(req.refreshToken());
    }

    private Optional<User> guest(Long currentUserId) {
        if (currentUserId == null) return Optional.empty();
        return users.findById(currentUserId).filter(u -> u.getRole() == Role.GUEST);
    }

    private AuthResponse respond(User user) {
        TokenService.IssuedTokens issued = tokens.issue(user);
        return new AuthResponse(issued.accessToken(), issued.expiresInSeconds(), issued.refreshToken(), UserDto.from(user));
    }
}
