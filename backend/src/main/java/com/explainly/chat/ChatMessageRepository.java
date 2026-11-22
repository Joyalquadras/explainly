package com.explainly.chat;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByUserIdAndAnalysisIdOrderByCreatedAtAscIdAsc(Long userId, Long analysisId);
    List<ChatMessage> findByUserIdAndAnalysisIdOrderByCreatedAtDescIdDesc(Long userId, Long analysisId, Pageable pageable);
    void deleteByUserIdAndAnalysisId(Long userId, Long analysisId);
}
