package com.example.demo.domain;

import java.time.Instant;

public record RateLimitExceededEvent(
        String eventId,
        String eventType,
        String apiKey,
        long usage,
        int limit,
        int windowSeconds,
        long windowTtl,
        Instant occurredAt) {

    public static final String TYPE = "RATE_LIMIT_EXCEEDED";
}
