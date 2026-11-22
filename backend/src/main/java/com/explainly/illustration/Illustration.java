package com.explainly.illustration;

import com.explainly.explanation.VideoChapter;
import com.explainly.video.VideoAnalysis;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "illustrations")
@Getter
@Setter
public class Illustration {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_id")
    private VideoAnalysis analysis;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chapter_id")
    private VideoChapter chapter;
    private int position;
    private String title;
    private String diagramType;
    private String explanation;
    private String example;
    private String specJson;
    private String svg;
    private String imageData;
    private String imageMime;
    private Instant createdAt;

    @PrePersist
    void onCreate() { createdAt = Instant.now(); }
}
