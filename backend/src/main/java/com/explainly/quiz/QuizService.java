package com.explainly.quiz;

import com.explainly.ai.AiService;
import com.explainly.ai.LlmRequest;
import com.explainly.ai.Prompts;
import com.explainly.common.ApiException;
import com.explainly.common.Json;
import com.explainly.explanation.LearningMaterial;
import com.explainly.video.AnalysisAccess;
import com.explainly.video.VideoAnalysis;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.*;

@Service
public class QuizService {

    private static final Set<String> TECHNICAL = Set.of("Programming", "Software Engineering", "AI & Machine Learning",
            "Data Science", "Cloud & DevOps", "Networking & Security");
    private final QuizRepository quizzes;
    private final QuizAttemptRepository attempts;
    private final AnalysisAccess access;
    private final LearningMaterial material;
    private final AiService ai;
    private final TransactionTemplate tx;

    public QuizService(QuizRepository quizzes, QuizAttemptRepository attempts, AnalysisAccess access,
                       LearningMaterial material, AiService ai, PlatformTransactionManager txManager) {
        this.quizzes = quizzes;
        this.attempts = attempts;
        this.access = access;
        this.material = material;
        this.ai = ai;
        this.tx = new TransactionTemplate(txManager);
    }

    /** Questions are sent without answers; answers are revealed when an attempt is graded. */
    public record QuestionDto(Long id, int position, QuizQuestion.QuestionType type, String question, String codeSnippet,
                              List<String> options, Integer timestampSeconds) {}

    public record QuizDto(Long id, Long analysisId, String title, Instant createdAt, List<QuestionDto> questions,
                          AttemptSummary bestAttempt, int attemptCount) {}

    public record AttemptSummary(Long id, int score, int total, int percent, Instant createdAt) {}

    public record QuestionResult(Long questionId, Integer selectedIndex, int correctIndex, boolean correct,
                                 String explanation, Integer timestampSeconds) {}

    public record AttemptResult(AttemptSummary attempt, List<QuestionResult> results) {}

    @Transactional(readOnly = true)
    public Optional<QuizDto> latest(Long userId, Long analysisId) {
        access.requireCompleted(userId, analysisId);
        return quizzes.findFirstByAnalysisIdOrderByCreatedAtDescIdDesc(analysisId).map(q -> toDto(q, userId));
    }

    public QuizDto generate(Long userId, Long analysisId) {
        record Input(String title, String material, boolean technical) {}
        Input input = tx.execute(s -> {
            VideoAnalysis a = access.requireCompleted(userId, analysisId);
            return new Input(a.getVideo().getTitle(), material.full(analysisId), TECHNICAL.contains(a.getCategory()));
        });
        JsonNode json = ai.completeJson(LlmRequest.json("quiz", Prompts.TEACHER,
                Prompts.quiz(input.title(), input.material(), input.technical(), 10), 10000));
        return tx.execute(s -> {
            Quiz quiz = new Quiz();
            quiz.setAnalysisId(analysisId);
            quiz.setTitle(clip(json.path("title").asText("Check your understanding"), 300));
            int pos = 0;
            for (JsonNode q : json.path("questions")) {
                if (pos >= 15) break;
                QuizQuestion qq = parseQuestion(q);
                if (qq == null) continue;
                qq.setQuiz(quiz);
                qq.setPosition(pos++);
                quiz.getQuestions().add(qq);
            }
            if (quiz.getQuestions().size() < 3) {
                throw ApiException.unprocessable("QUIZ_GENERATION_FAILED", "We couldn't create a good quiz this time. Please try again.");
            }
            return toDto(quizzes.save(quiz), userId);
        });
    }

    @Transactional
    public AttemptResult submit(Long userId, Long quizId, List<Integer> answers) {
        Quiz quiz = quizzes.findById(quizId).orElseThrow(() -> ApiException.notFound("Quiz"));
        access.require(userId, quiz.getAnalysisId());
        List<QuizQuestion> questions = quiz.getQuestions();
        if (answers == null || answers.size() != questions.size()) {
            throw ApiException.badRequest("ANSWER_COUNT_MISMATCH", "Please answer every question before submitting.");
        }
        List<QuestionResult> results = new ArrayList<>();
        int score = 0;
        for (int i = 0; i < questions.size(); i++) {
            QuizQuestion q = questions.get(i);
            Integer selected = answers.get(i);
            boolean correct = selected != null && selected == q.getCorrectIndex();
            if (correct) score++;
            results.add(new QuestionResult(q.getId(), selected, q.getCorrectIndex(), correct, q.getExplanation(), q.getTimestampSeconds()));
        }
        QuizAttempt attempt = new QuizAttempt();
        attempt.setQuizId(quizId);
        attempt.setUserId(userId);
        attempt.setScore(score);
        attempt.setTotal(questions.size());
        attempt.setAnswersJson(Json.write(answers));
        attempts.save(attempt);
        return new AttemptResult(summary(attempt), results);
    }

    @Transactional(readOnly = true)
    public List<AttemptSummary> attempts(Long userId, Long quizId) {
        Quiz quiz = quizzes.findById(quizId).orElseThrow(() -> ApiException.notFound("Quiz"));
        access.require(userId, quiz.getAnalysisId());
        return attempts.findByQuizIdAndUserIdOrderByCreatedAtDesc(quizId, userId).stream().map(QuizService::summary).toList();
    }

    static QuizQuestion parseQuestion(JsonNode q) {
        String text = q.path("question").asText("").trim();
        if (text.isEmpty()) return null;
        QuizQuestion.QuestionType type;
        try {
            type = QuizQuestion.QuestionType.valueOf(q.path("type").asText("MULTIPLE_CHOICE").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            type = QuizQuestion.QuestionType.MULTIPLE_CHOICE;
        }
        List<String> options = new ArrayList<>();
        for (JsonNode o : q.path("options")) {
            String s = o.asText("").trim();
            if (!s.isEmpty()) options.add(clip(s, 500));
        }
        if (type == QuizQuestion.QuestionType.TRUE_FALSE) options = List.of("True", "False");
        if (options.size() < 2 || options.size() > 6) return null;
        int correct = q.path("correctIndex").asInt(-1);
        if (type == QuizQuestion.QuestionType.TRUE_FALSE && q.path("correctIndex").isMissingNode()) {
            correct = q.path("answer").asBoolean() ? 0 : 1;
        }
        if (correct < 0 || correct >= options.size()) return null;
        QuizQuestion qq = new QuizQuestion();
        qq.setType(type);
        qq.setQuestion(clip(text, 2000));
        String code = q.path("codeSnippet").isTextual() ? q.path("codeSnippet").asText().trim() : null;
        qq.setCodeSnippet(code == null || code.isEmpty() ? null : clip(code, 4000));
        qq.setOptionsJson(Json.write(options));
        qq.setCorrectIndex(correct);
        qq.setExplanation(clip(q.path("explanation").asText(""), 2000));
        qq.setTimestampSeconds(q.path("timestampSeconds").isNumber() ? Math.max(0, q.path("timestampSeconds").asInt()) : null);
        return qq;
    }

    private QuizDto toDto(Quiz q, Long userId) {
        List<QuestionDto> questions = q.getQuestions().stream().map(qq -> new QuestionDto(qq.getId(), qq.getPosition(), qq.getType(),
                qq.getQuestion(), qq.getCodeSnippet(), Json.read(qq.getOptionsJson(), new TypeReference<List<String>>() {}),
                qq.getTimestampSeconds())).toList();
        List<QuizAttempt> mine = q.getId() == null ? List.of() : attempts.findByQuizIdAndUserIdOrderByCreatedAtDesc(q.getId(), userId);
        AttemptSummary best = mine.stream().max(Comparator.comparingInt(QuizAttempt::getScore)).map(QuizService::summary).orElse(null);
        return new QuizDto(q.getId(), q.getAnalysisId(), q.getTitle(), q.getCreatedAt(), questions, best, mine.size());
    }

    private static AttemptSummary summary(QuizAttempt a) {
        return new AttemptSummary(a.getId(), a.getScore(), a.getTotal(),
                a.getTotal() == 0 ? 0 : (int) Math.round(100.0 * a.getScore() / a.getTotal()), a.getCreatedAt());
    }

    private static String clip(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
