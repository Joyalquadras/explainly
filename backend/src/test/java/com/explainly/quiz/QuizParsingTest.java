package com.explainly.quiz;

import com.explainly.common.Json;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QuizParsingTest {
    @Test
    void normalisesTrueFalseAndRejectsBadIndexes() {
        QuizQuestion tf = QuizService.parseQuestion(Json.tree("{\"type\":\"true_false\",\"question\":\"Q\",\"options\":[\"yes\"],\"correctIndex\":1}"));
        assertThat(tf).isNotNull();
        assertThat(tf.getOptionsJson()).isEqualTo("[\"True\",\"False\"]");
        assertThat(QuizService.parseQuestion(Json.tree("{\"question\":\"Q\",\"options\":[\"a\",\"b\"],\"correctIndex\":5}"))).isNull();
        assertThat(QuizService.parseQuestion(Json.tree("{\"question\":\"\",\"options\":[\"a\",\"b\"],\"correctIndex\":0}"))).isNull();
    }
}
