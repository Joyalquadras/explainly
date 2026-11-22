package com.explainly;

import com.explainly.support.TestAiConfig;
import org.springframework.boot.SpringApplication;

/**
 * Local development entry point that runs the real application with the deterministic test LLM, for
 * exercising the UI without an AI provider key: {@code mvn spring-boot:test-run}. Never used in production.
 */
public class TestExplainlyApplication {
    public static void main(String[] args) {
        SpringApplication.from(ExplainlyApplication::main)
                .with(TestAiConfig.class)
                .run(args);
    }
}
