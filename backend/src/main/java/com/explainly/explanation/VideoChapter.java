package com.explainly.explanation;

import com.explainly.video.VideoAnalysis;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "video_chapters")
@Getter
@Setter
public class VideoChapter {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_id")
    private VideoAnalysis analysis;
    private int position;
    private String title;
    private Integer startSeconds;
    private Integer endSeconds;
    private String shortSummary;
    private String simpleExplanation;
    private String detailedExplanation;
    private String realWorldExample;
    private String practicalUseCase;
    private String keyPointsJson;
    private String conceptsJson;
}
