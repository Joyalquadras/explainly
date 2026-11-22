package com.explainly.ai;

import java.util.List;

/**
 * Provider-neutral completion request.
 * @param purpose short label used for logging/metrics (e.g. "explanation", "chat")
 */
public record LlmRequest(String system, List<Message> messages, int maxTokens, boolean jsonOutput, String purpose) {

    public record Message(Role role, String content) {}

    public enum Role { USER, ASSISTANT }

    public static LlmRequest json(String purpose, String system, String user, int maxTokens) {
        return new LlmRequest(system, List.of(new Message(Role.USER, user)), maxTokens, true, purpose);
    }

    public static LlmRequest text(String purpose, String system, List<Message> messages, int maxTokens) {
        return new LlmRequest(system, messages, maxTokens, false, purpose);
    }
}
