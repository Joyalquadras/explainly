package com.explainly.explanation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VideoChapterRepository extends JpaRepository<VideoChapter, Long> {
    List<VideoChapter> findByAnalysisIdOrderByPositionAsc(Long analysisId);
    long countByAnalysisId(Long analysisId);
    void deleteByAnalysisId(Long analysisId);
}
