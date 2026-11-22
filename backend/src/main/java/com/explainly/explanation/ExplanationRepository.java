package com.explainly.explanation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ExplanationRepository extends JpaRepository<Explanation, Long> {
    Optional<Explanation> findByAnalysisId(Long analysisId);
    void deleteByAnalysisId(Long analysisId);
}
