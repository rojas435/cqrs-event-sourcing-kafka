package com.techbank.account.query.infrastructure.consumers;

import org.springframework.kafka.support.Acknowledgment;

public interface EventConsumer {
    void consume(Object event, Acknowledgment ack, String eventType);
}
