package com.explainly.user;

import com.explainly.common.ApiException;
import com.explainly.common.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/me")
@Tag(name = "User")
public class UserController {

    private final UserRepository users;
    private final com.explainly.youtube.YouTubeOAuthService youtube;

    public UserController(UserRepository users, com.explainly.youtube.YouTubeOAuthService youtube) {
        this.users = users;
        this.youtube = youtube;
    }

    public record UpdateProfileRequest(@NotBlank @Size(max = 120) String displayName) {}

    @GetMapping
    public UserDto me() {
        return UserDto.from(load());
    }

    @PatchMapping
    @Transactional
    public UserDto update(@Valid @RequestBody UpdateProfileRequest req) {
        User user = load();
        user.setDisplayName(req.displayName().trim());
        return UserDto.from(user);
    }

    /** Permanently deletes the account and all learning data (cascades in the database). */
    @DeleteMapping
    @Transactional
    public ResponseEntity<Void> delete() {
        User user = load();
        youtube.disconnect(user.getId()); // revoke the Google grant before the account disappears
        users.delete(user);
        return ResponseEntity.noContent().build();
    }

    private User load() {
        return users.findById(CurrentUser.id()).orElseThrow(() -> ApiException.notFound("User"));
    }
}
