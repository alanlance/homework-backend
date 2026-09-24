package com.example.demo.service;

import com.example.demo.domain.RateLimitRule;
import com.example.demo.repository.RateLimitRuleRepository;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@CacheConfig(cacheNames = RateLimitRuleLookupService.CACHE_NAME)
public class RateLimitRuleLookupService {

    static final String CACHE_NAME = "rateLimitRules";

    private final RateLimitRuleRepository repository;

    public RateLimitRuleLookupService(RateLimitRuleRepository repository) {
        this.repository = repository;
    }

    @Cacheable(key = "#apiKey")
    public RateLimitRule findByApiKey(String apiKey) {
        return repository.findByApiKey(apiKey)
                .orElseThrow(() -> new RuleNotFoundException(apiKey));
    }

    @CachePut(key = "#rule.apiKey()")
    public RateLimitRule put(RateLimitRule rule) {
        return rule;
    }

    @CacheEvict(key = "#apiKey")
    public void evict(String apiKey) {
        // Spring Cache 會透過方法攔截清除 Redis 快取，不需要額外方法內容。
    }
}
