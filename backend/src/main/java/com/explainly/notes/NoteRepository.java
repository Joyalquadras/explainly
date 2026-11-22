package com.explainly.notes;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NoteRepository extends JpaRepository<Note, Long> {
    Optional<Note> findByUserIdAndAnalysisId(Long userId, Long analysisId);
    long countByUserId(Long userId);
}
