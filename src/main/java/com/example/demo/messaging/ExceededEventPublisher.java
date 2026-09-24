package com.example.demo.messaging;

import com.example.demo.domain.RateLimitExceededEvent;

public interface ExceededEventPublisher {

    void publish(RateLimitExceededEvent event);
}
