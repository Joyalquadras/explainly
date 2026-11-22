package com.explainly.ai;

/** Raised when an AI provider call fails. The message is safe to show to users. */
public class AiProviderException extends RuntimeException {
    public AiProviderException(String message) { super(message); }
    public AiProviderException(String message, Throwable cause) { super(message, cause); }
}
