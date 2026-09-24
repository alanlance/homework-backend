package com.example.demo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.demo.domain.RateLimitExceededEvent;
import com.example.demo.domain.RateLimitRule;
import com.example.demo.messaging.ExceededEventPublisher;
import com.example.demo.redis.RateLimitCounter;
import com.example.demo.repository.RateLimitRuleRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class RateLimitServiceTests {

    private static final Instant NOW = Instant.parse("2026-09-24T13:30:00Z");

    @Mock
    private RateLimitRuleRepository ruleRepository;

    @Mock
    private RateLimitRuleLookupService ruleLookupService;

    @Mock
    private RateLimitCounter counter;

    @Mock
    private ExceededEventPublisher eventPublisher;

    private RateLimitService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new RateLimitService(
                ruleRepository,
                ruleLookupService,
                counter,
                eventPublisher,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void publishesExactlyOneEventWhenUsageFirstExceedsLimit() {
        when(ruleLookupService.findByApiKey("abc-123")).thenReturn(rule(2, 60));
        when(counter.increment("abc-123", 60)).thenReturn(
                new RateLimitCounter.CounterSnapshot(1, 60),
                new RateLimitCounter.CounterSnapshot(2, 55),
                new RateLimitCounter.CounterSnapshot(3, 50),
                new RateLimitCounter.CounterSnapshot(4, 45));

        assertThat(service.check("abc-123").allowed()).isTrue();
        assertThat(service.check("abc-123").allowed()).isTrue();
        assertThat(service.check("abc-123").allowed()).isFalse();
        assertThat(service.check("abc-123").allowed()).isFalse();

        ArgumentCaptor<RateLimitExceededEvent> eventCaptor =
                ArgumentCaptor.forClass(RateLimitExceededEvent.class);
        verify(eventPublisher, times(1)).publish(eventCaptor.capture());
        RateLimitExceededEvent event = eventCaptor.getValue();
        assertThat(event.apiKey()).isEqualTo("abc-123");
        assertThat(event.usage()).isEqualTo(3);
        assertThat(event.limit()).isEqualTo(2);
        assertThat(event.windowTtl()).isEqualTo(50);
        assertThat(event.occurredAt()).isEqualTo(NOW);
    }

    @Test
    void reportsZeroUsageAndFullQuotaWhenNoWindowExists() {
        when(ruleLookupService.findByApiKey("abc-123")).thenReturn(rule(100, 60));
        when(counter.get("abc-123")).thenReturn(new RateLimitCounter.CounterSnapshot(0, 0));

        var result = service.usage("abc-123");

        assertThat(result.usage()).isZero();
        assertThat(result.remaining()).isEqualTo(100);
        assertThat(result.ttl()).isZero();
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void rejectsCheckWhenRuleDoesNotExist() {
        when(ruleLookupService.findByApiKey("missing")).thenThrow(new RuleNotFoundException("missing"));

        assertThatThrownBy(() -> service.check("missing"))
                .isInstanceOf(RuleNotFoundException.class);
        verifyNoInteractions(counter, eventPublisher);
    }

    private static RateLimitRule rule(int requestLimit, int windowSeconds) {
        return new RateLimitRule(1, "abc-123", requestLimit, windowSeconds, NOW, NOW);
    }
}
