package com.example.demo.redis;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class RateLimitCounter {

    private static final DefaultRedisScript<List> INCREMENT_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            local ttl = redis.call('TTL', KEYS[1])
            return {current, ttl}
            """, List.class);

    private final StringRedisTemplate redisTemplate;

    public RateLimitCounter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public CounterSnapshot increment(String apiKey, int windowSeconds) {
        List<?> result = redisTemplate.execute(
                INCREMENT_SCRIPT,
                List.of(redisKey(apiKey)),
                Integer.toString(windowSeconds));
        if (result == null || result.size() != 2) {
            throw new IllegalStateException("Redis did not return the rate-limit counter result");
        }
        return new CounterSnapshot(asLong(result.get(0)), asLong(result.get(1)));
    }

    public CounterSnapshot get(String apiKey) {
        String key = redisKey(apiKey);
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return new CounterSnapshot(0, 0);
        }

        Long ttl = redisTemplate.getExpire(key);
        if (ttl == null || ttl <= 0) {
            return new CounterSnapshot(0, 0);
        }
        return new CounterSnapshot(Long.parseLong(value), ttl);
    }

    public void clear(String apiKey) {
        redisTemplate.delete(redisKey(apiKey));
    }

    private static String redisKey(String apiKey) {
        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(apiKey.getBytes(StandardCharsets.UTF_8));
        return "rate-limit:usage:" + encoded;
    }

    private static long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }

    public record CounterSnapshot(long usage, long ttl) {
    }
}
