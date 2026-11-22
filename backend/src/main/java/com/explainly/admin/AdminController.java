package com.explainly.admin;

import com.explainly.user.Role;
import com.explainly.user.UserRepository;
import com.explainly.video.AnalysisStatus;
import com.explainly.video.VideoAnalysisRepository;
import com.explainly.video.VideoRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.EnumMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin")
public class AdminController {

    private final UserRepository users;
    private final VideoRepository videos;
    private final VideoAnalysisRepository analyses;

    public AdminController(UserRepository users, VideoRepository videos, VideoAnalysisRepository analyses) {
        this.users = users;
        this.videos = videos;
        this.analyses = analyses;
    }

    public record Stats(long users, long guests, long videos, Map<AnalysisStatus, Long> analysesByStatus) {}

    @GetMapping("/stats")
    public Stats stats() {
        Map<AnalysisStatus, Long> byStatus = new EnumMap<>(AnalysisStatus.class);
        for (AnalysisStatus s : AnalysisStatus.values()) byStatus.put(s, analyses.countByStatus(s));
        return new Stats(users.count(), users.countByRole(Role.GUEST), videos.count(), byStatus);
    }
}
