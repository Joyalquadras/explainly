package com.explainly.explanation;

import com.explainly.video.VideoAnalysis;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "explanations")
@Getter
@Setter
public class Explanation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_id")
    private VideoAnalysis analysis;
    private String overviewJson;
    private String beginnerExplanation;
    private String technicalExplanation;
    private String conceptsJson;
    private String summaryShort;
    private String summaryMedium;
    private String summaryDetailed;
    private String generatedNotes;
    private Instant createdAt;

    @PrePersist
    void onCreate() { createdAt = Instant.now(); }
}
