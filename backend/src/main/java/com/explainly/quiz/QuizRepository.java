package com.explainly.quiz;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface QuizRepository extends JpaRepository<Quiz, Long> {
    Optional<Quiz> findFirstByAnalysisIdOrderByCreatedAtDescIdDesc(Long analysisId);
}
