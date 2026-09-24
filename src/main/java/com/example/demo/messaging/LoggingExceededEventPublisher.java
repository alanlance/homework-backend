package com.example.demo.messaging;

import com.example.demo.domain.RateLimitExceededEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.rocketmq.enabled", havingValue = "false")
public class LoggingExceededEventPublisher implements ExceededEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingExceededEventPublisher.class);

    @Override
    public void publish(RateLimitExceededEvent event) {
        log.debug("RocketMQ is disabled; skipped event {}", event.eventId());
    }
}
