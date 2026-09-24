package com.example.demo.domain;

import java.io.Serializable;
import java.time.Instant;

public record RateLimitRule(
        long id,
        String apiKey,
        int requestLimit,
        int windowSeconds,
        Instant createdAt,
        Instant updatedAt) implements Serializable {
}
