package com.example.demo.api;

import com.example.demo.domain.RateLimitRule;
import java.time.Instant;

public record LimitResponse(
        long id,
        String apiKey,
        int limit,
        int windowSeconds,
        Instant createdAt,
        Instant updatedAt) {

    public static LimitResponse from(RateLimitRule rule) {
        return new LimitResponse(
                rule.id(),
                rule.apiKey(),
                rule.requestLimit(),
                rule.windowSeconds(),
                rule.createdAt(),
                rule.updatedAt());
    }
}
