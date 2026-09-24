package com.example.demo.messaging;

import com.example.demo.domain.RateLimitExceededEvent;
import com.example.demo.repository.ExceededEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

@Component
@DependsOn("rocketMqExceededEventPublisher")
@ConditionalOnProperty(name = "app.rocketmq.enabled", havingValue = "true", matchIfMissing = true)
public class RocketMqExceededEventConsumer implements InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(RocketMqExceededEventConsumer.class);

    private final RocketMqProperties properties;
    private final ObjectMapper objectMapper;
    private final ExceededEventRepository repository;
    private DefaultMQPushConsumer consumer;

    public RocketMqExceededEventConsumer(
            RocketMqProperties properties,
            ObjectMapper objectMapper,
            ExceededEventRepository repository) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.repository = repository;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        consumer = new DefaultMQPushConsumer(properties.getConsumerGroup());
        consumer.setNamesrvAddr(properties.getNameServer());
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        consumer.subscribe(properties.getTopic(), properties.getTag());
        consumer.registerMessageListener((MessageListenerConcurrently) (messages, context) -> {
            try {
                for (var message : messages) {
                    RateLimitExceededEvent event = objectMapper.readValue(
                            message.getBody(), RateLimitExceededEvent.class);
                    repository.saveIdempotently(event);
                    log.info("Consumed rate-limit exceeded event: eventId={}", event.eventId());
                }
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            } catch (Exception error) {
                log.error("Failed to consume rate-limit exceeded event", error);
                return ConsumeConcurrentlyStatus.RECONSUME_LATER;
            }
        });
        consumer.start();
    }

    @Override
    public void destroy() {
        if (consumer != null) {
            consumer.shutdown();
        }
    }
}
