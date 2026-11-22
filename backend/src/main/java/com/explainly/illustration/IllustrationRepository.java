package com.explainly.illustration;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IllustrationRepository extends JpaRepository<Illustration, Long> {
    List<Illustration> findByAnalysisIdOrderByPositionAsc(Long analysisId);
    void deleteByAnalysisId(Long analysisId);
}
