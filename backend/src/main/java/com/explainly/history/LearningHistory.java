package com.explainly.history;

import com.explainly.video.VideoAnalysis;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "learning_history")
@Getter
@Setter
public class LearningHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long userId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_id")
    private VideoAnalysis analysis;
    private int progressPercent;
    private String completedChaptersJson = "[]";
    private Integer lastPositionSeconds;
    private Instant lastAccessedAt;
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        if (lastAccessedAt == null) lastAccessedAt = createdAt;
    }
}
