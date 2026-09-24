package com.example.demo.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.demo.domain.RateLimitRule;
import com.example.demo.repository.RateLimitRuleRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(RateLimitRuleLookupServiceTests.TestConfiguration.class)
class RateLimitRuleLookupServiceTests {

    @Autowired
    private RateLimitRuleLookupService service;

    @Autowired
    private CountingRuleRepository repository;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        repository.reset();
        cacheManager.getCache(RateLimitRuleLookupService.CACHE_NAME).clear();
    }

    @Test
    void cachesRuleAfterFirstRepositoryLookup() {
        RateLimitRule first = service.findByApiKey("abc-123");
        RateLimitRule second = service.findByApiKey("abc-123");

        assertThat(second).isEqualTo(first);
        assertThat(repository.lookupCount()).isEqualTo(1);
    }

    @Test
    void refreshesAndEvictsCachedRule() {
        service.findByApiKey("abc-123");
        RateLimitRule updated = rule(200);

        service.put(updated);
        assertThat(service.findByApiKey("abc-123").requestLimit()).isEqualTo(200);
        assertThat(repository.lookupCount()).isEqualTo(1);

        service.evict("abc-123");
        service.findByApiKey("abc-123");
        assertThat(repository.lookupCount()).isEqualTo(2);
    }

    private static RateLimitRule rule(int requestLimit) {
        Instant now = Instant.parse("2026-09-24T13:30:00Z");
        return new RateLimitRule(1, "abc-123", requestLimit, 60, now, now);
    }

    @Configuration
    @EnableCaching
    static class TestConfiguration {

        @Bean
        CountingRuleRepository repository() {
            return new CountingRuleRepository();
        }

        @Bean
        RateLimitRuleLookupService service(CountingRuleRepository repository) {
            return new RateLimitRuleLookupService(repository);
        }

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(RateLimitRuleLookupService.CACHE_NAME);
        }
    }

    static class CountingRuleRepository extends RateLimitRuleRepository {

        private int lookupCount;

        CountingRuleRepository() {
            super((JdbcTemplate) null);
        }

        @Override
        public Optional<RateLimitRule> findByApiKey(String apiKey) {
            lookupCount++;
            return Optional.of(rule(100));
        }

        int lookupCount() {
            return lookupCount;
        }

        void reset() {
            lookupCount = 0;
        }
    }
}
