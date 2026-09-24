package com.example.demo.messaging;

import com.example.demo.domain.RateLimitExceededEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.rocketmq.enabled", havingValue = "true", matchIfMissing = true)
public class RocketMqExceededEventPublisher
        implements ExceededEventPublisher, InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(RocketMqExceededEventPublisher.class);

    private final RocketMqProperties properties;
    private final ObjectMapper objectMapper;
    private DefaultMQProducer producer;

    public RocketMqExceededEventPublisher(RocketMqProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterPropertiesSet() throws MQClientException {
        producer = new DefaultMQProducer(properties.getProducerGroup());
        producer.setNamesrvAddr(properties.getNameServer());
        producer.start();
    }

    @Override
    public void publish(RateLimitExceededEvent event) {
        try {
            byte[] body = objectMapper.writeValueAsBytes(event);
            Message message = new Message(properties.getTopic(), properties.getTag(), event.eventId(), body);
            producer.send(message, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    log.info("Published rate-limit exceeded event: eventId={}, messageId={}",
                            event.eventId(), sendResult.getMsgId());
                }

                @Override
                public void onException(Throwable error) {
                    log.error("Failed to publish rate-limit exceeded event: eventId={}",
                            event.eventId(), error);
                }
            });
        } catch (Exception error) {
            if (error instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.error("Failed to dispatch rate-limit exceeded event: eventId={}", event.eventId(), error);
        }
    }

    @Override
    public void destroy() {
        if (producer != null) {
            producer.shutdown();
        }
    }
}
