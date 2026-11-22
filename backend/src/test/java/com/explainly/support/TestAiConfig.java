package com.explainly.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class TestAiConfig {
    @Bean
    public ScriptedLlmClient scriptedLlmClient() {
        return new ScriptedLlmClient();
    }
}
