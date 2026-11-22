package com.explainly.history;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface LearningHistoryRepository extends JpaRepository<LearningHistory, Long> {
    Optional<LearningHistory> findByUserIdAndAnalysisId(Long userId, Long analysisId);
    boolean existsByUserIdAndAnalysisId(Long userId, Long analysisId);
    List<LearningHistory> findByAnalysisId(Long analysisId);
    long countByUserId(Long userId);
    long countByUserIdAndProgressPercent(Long userId, int progressPercent);

    @EntityGraph(attributePaths = {"analysis", "analysis.video"})
    Page<LearningHistory> findByUserId(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"analysis", "analysis.video"})
    Page<LearningHistory> findByUserIdAndAnalysisCategory(Long userId, String category, Pageable pageable);

    @EntityGraph(attributePaths = {"analysis", "analysis.video"})
    @Query("select h from LearningHistory h where h.userId = :userId and h.analysis.video.id = :videoId order by h.lastAccessedAt desc")
    List<LearningHistory> findByUserIdAndVideoId(Long userId, Long videoId);

    @Query("select h.analysis.category, count(h) from LearningHistory h where h.userId = :userId and h.analysis.category is not null group by h.analysis.category")
    List<Object[]> countCategories(Long userId);
}
