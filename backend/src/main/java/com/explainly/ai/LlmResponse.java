package com.explainly.ai;

public record LlmResponse(String text, String provider, String model, boolean truncated) {}
