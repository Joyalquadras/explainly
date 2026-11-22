package com.explainly.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class JsonExtractorTest {
    @Test
    void extractsFromFencesAndProse() {
        assertThat(JsonExtractor.extractObject("```json\n{\"a\":1}\n```").path("a").asInt()).isEqualTo(1);
        assertThat(JsonExtractor.extractObject("Sure! {\"a\":{\"b\":2}} hope that helps").path("a").path("b").asInt()).isEqualTo(2);
    }

    @Test
    void rejectsInvalid() {
        assertThatThrownBy(() -> JsonExtractor.extractObject("no json here")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> JsonExtractor.extractObject("{\"a\": ")).isInstanceOf(IllegalArgumentException.class);
    }
}
