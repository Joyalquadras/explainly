package com.explainly.transcript;

/** A piece of transcript text. {@code start}/{@code end} are seconds, or null for untimed text. */
public record TranscriptSegment(Double start, Double end, String text) {}
