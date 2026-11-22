package com.explainly.ai;

/** Abstraction over large-language-model providers. Add new providers by implementing this interface. */
public interface LlmClient {
    /** Stable identifier used in configuration, e.g. "anthropic" or "openai". */
    String id();

    String model();

    boolean isConfigured();

    LlmResponse complete(LlmRequest request);
}
