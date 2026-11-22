package com.explainly.quiz;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "quizzes")
@Getter
@Setter
public class Quiz {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long analysisId;
    private String title;
    private Instant createdAt;
    @OneToMany(mappedBy = "quiz", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position asc")
    private List<QuizQuestion> questions = new ArrayList<>();

    @PrePersist
    void onCreate() { createdAt = Instant.now(); }
}
