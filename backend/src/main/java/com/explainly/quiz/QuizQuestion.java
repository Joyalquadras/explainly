package com.explainly.quiz;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "quiz_questions")
@Getter
@Setter
public class QuizQuestion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quiz_id")
    private Quiz quiz;
    private int position;
    @Enumerated(EnumType.STRING)
    @Column(name = "question_type")
    private QuestionType type;
    private String question;
    private String codeSnippet;
    private String optionsJson;
    private int correctIndex;
    private String explanation;
    private Integer timestampSeconds;

    public enum QuestionType { MULTIPLE_CHOICE, TRUE_FALSE, CODE }
}
