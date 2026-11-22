package com.explainly.bookmarks;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {
    Page<Bookmark> findByUserId(Long userId, Pageable pageable);
    Page<Bookmark> findByUserIdAndAnalysisId(Long userId, Long analysisId, Pageable pageable);
    long countByUserId(Long userId);
}
