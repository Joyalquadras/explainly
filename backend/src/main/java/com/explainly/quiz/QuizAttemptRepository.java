package com.explainly.quiz;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {
    List<QuizAttempt> findByQuizIdAndUserIdOrderByCreatedAtDesc(Long quizId, Long userId);
    long countByUserId(Long userId);

    @Query("select coalesce(avg(100.0 * a.score / a.total), 0) from QuizAttempt a where a.userId = :userId and a.total > 0")
    double averagePercentForUser(Long userId);
}
