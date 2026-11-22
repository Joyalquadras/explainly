package com.explainly.flashcards;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FlashcardRepository extends JpaRepository<Flashcard, Long> {
    List<Flashcard> findByAnalysisIdOrderByPositionAsc(Long analysisId);
    void deleteByAnalysisId(Long analysisId);
}
