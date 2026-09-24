package com.example.demo.repository;

import com.example.demo.domain.RateLimitExceededEvent;
import java.sql.Timestamp;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ExceededEventRepository {

    private final JdbcTemplate jdbcTemplate;

    public ExceededEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void saveIdempotently(RateLimitExceededEvent event) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO rate_limit_exceeded_events (
                        event_id, api_key, usage_count, request_limit,
                        window_seconds, window_ttl, occurred_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?)
                    """,
                    event.eventId(),
                    event.apiKey(),
                    event.usage(),
                    event.limit(),
                    event.windowSeconds(),
                    event.windowTtl(),
                    Timestamp.from(event.occurredAt()));
        } catch (DuplicateKeyException ignored) {
            // RocketMQ guarantees at-least-once delivery. The event ID makes retries idempotent.
        }
    }
}
