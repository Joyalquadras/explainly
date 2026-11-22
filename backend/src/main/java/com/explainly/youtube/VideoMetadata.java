package com.explainly.youtube;

import java.time.Instant;

public record VideoMetadata(String videoId, String title, String channelTitle, String description,
                            String thumbnailUrl, Integer durationSeconds, Instant publishedAt, boolean captionsAvailable) {}
