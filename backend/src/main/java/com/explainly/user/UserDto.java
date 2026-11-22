package com.explainly.user;

import java.time.Instant;

public record UserDto(Long id, String email, String displayName, String avatarUrl, Role role,
                      AuthProvider authProvider, Instant createdAt) {
    public static UserDto from(User u) {
        return new UserDto(u.getId(), u.getEmail(), u.getDisplayName(), u.getAvatarUrl(), u.getRole(),
                u.getAuthProvider(), u.getCreatedAt());
    }
}
