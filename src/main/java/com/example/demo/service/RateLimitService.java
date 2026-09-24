package com.example.demo.service;

import com.example.demo.domain.RateLimitExceededEvent;
import com.example.demo.domain.RateLimitRule;
import com.example.demo.messaging.ExceededEventPublisher;
import com.example.demo.redis.RateLimitCounter;
import com.example.demo.repository.RateLimitRuleRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RateLimitService {

    private final RateLimitRuleRepository ruleRepository;
    private final RateLimitRuleLookupService ruleLookupService;
    private final RateLimitCounter counter;
    private final ExceededEventPublisher eventPublisher;
    private final Clock clock;

    @Autowired
    public RateLimitService(
            RateLimitRuleRepository ruleRepository,
            RateLimitRuleLookupService ruleLookupService,
            RateLimitCounter counter,
            ExceededEventPublisher eventPublisher) {
        this(ruleRepository, ruleLookupService, counter, eventPublisher, Clock.systemUTC());
    }

    RateLimitService(
            RateLimitRuleRepository ruleRepository,
            RateLimitRuleLookupService ruleLookupService,
            RateLimitCounter counter,
            ExceededEventPublisher eventPublisher,
            Clock clock) {
        this.ruleRepository = ruleRepository;
        this.ruleLookupService = ruleLookupService;
        this.counter = counter;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public UpsertResult upsertRule(String apiKey, int requestLimit, int windowSeconds) {
        var existing = ruleRepository.findByApiKey(apiKey);
        RateLimitRule rule;
        boolean created;
        if (existing.isPresent()) {
            rule = ruleRepository.update(existing.get().id(), requestLimit, windowSeconds);
            created = false;
        } else {
            rule = ruleRepository.insert(apiKey, requestLimit, windowSeconds);
            created = true;
        }
        counter.clear(apiKey);
        ruleLookupService.put(rule);
        return new UpsertResult(rule, created);
    }

    public CheckResult check(String apiKey) {
        // 限流規則必須先存在；找不到時會拋出例外並由 API 層回傳 404。
        RateLimitRule rule = ruleLookupService.findByApiKey(apiKey);

        // Redis Lua 腳本會原子執行 INCR，並只在首次請求時設定視窗 TTL。
        RateLimitCounter.CounterSnapshot snapshot = counter.increment(apiKey, rule.windowSeconds());

        // 使用量等於上限時仍可通過；剩餘配額最低為 0，不回傳負數。
        boolean allowed = snapshot.usage() <= rule.requestLimit();
        long remaining = Math.max(0, rule.requestLimit() - snapshot.usage());

        // 只在每個時間視窗第一次超額時發送事件，避免後續阻擋請求造成事件風暴。
        if (snapshot.usage() == (long) rule.requestLimit() + 1) {
            eventPublisher.publish(new RateLimitExceededEvent(
                    UUID.randomUUID().toString(),
                    RateLimitExceededEvent.TYPE,
                    apiKey,
                    snapshot.usage(),
                    rule.requestLimit(),
                    rule.windowSeconds(),
                    Math.max(0, snapshot.ttl()),
                    Instant.now(clock)));
        }

        // TTL 可能在視窗到期瞬間變成負值，因此對外回傳值最低為 0。
        return new CheckResult(apiKey, allowed, snapshot.usage(), remaining, Math.max(0, snapshot.ttl()));
    }

    public UsageResult usage(String apiKey) {
        RateLimitRule rule = ruleLookupService.findByApiKey(apiKey);
        RateLimitCounter.CounterSnapshot snapshot = counter.get(apiKey);
        long remaining = Math.max(0, rule.requestLimit() - snapshot.usage());
        return new UsageResult(apiKey, snapshot.usage(), remaining, snapshot.ttl());
    }

    public PageResult listRules(int page, int size) {
        long offset = (long) page * size;
        List<RateLimitRule> rules = ruleRepository.findPage(offset, size);
        long totalElements = ruleRepository.count();
        int totalPages = totalElements == 0 ? 0 : (int) ((totalElements + size - 1) / size);
        return new PageResult(rules, page, size, totalElements, totalPages);
    }

    @Transactional
    public void deleteRule(String apiKey) {
        if (!ruleRepository.deleteByApiKey(apiKey)) {
            throw new RuleNotFoundException(apiKey);
        }
        counter.clear(apiKey);
        ruleLookupService.evict(apiKey);
    }

    public record UpsertResult(RateLimitRule rule, boolean created) {
    }

    public record CheckResult(String apiKey, boolean allowed, long usage, long remaining, long ttl) {
    }

    public record UsageResult(String apiKey, long usage, long remaining, long ttl) {
    }

    public record PageResult(
            List<RateLimitRule> content,
            int page,
            int size,
            long totalElements,
            int totalPages) {
    }
}
