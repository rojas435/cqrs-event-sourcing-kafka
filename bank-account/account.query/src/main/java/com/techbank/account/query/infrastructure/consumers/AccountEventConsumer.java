package com.techbank.account.query.infrastructure.consumers;

import com.techbank.account.common.events.AccountClosedEvent;
import com.techbank.account.common.events.AccountOpenedEvent;
import com.techbank.account.common.events.FundsDepositedEvent;
import com.techbank.account.common.events.FundsWithdrawnEvent;
import com.techbank.account.query.infrastructure.handlers.EventHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

@Service
public class AccountEventConsumer implements EventConsumer {
    @Autowired
    private EventHandler eventHandler;

    @Autowired
    private ObjectMapper objectMapper;

    private final Logger logger = Logger.getLogger(AccountEventConsumer.class.getName());

    @KafkaListener(topics = "${kafka.topic}", groupId = "${spring.kafka.consumer.group-id}")
    @Override
    public void consume(@Payload Object payload, Acknowledgment ack, @Header(value = "eventType", required = false) String eventType) {
        try {
            Object message = payload;
            if (payload instanceof ConsumerRecord) {
                message = ((ConsumerRecord<?, ?>) payload).value();
            }
            if (eventType == null) {
                // fallback: log and ack
                logger.log(Level.WARNING, "Received event without eventType header");
                ack.acknowledge();
                return;
            }
            switch (eventType) {
                case "AccountOpenedEvent":
                    var opened = objectMapper.convertValue(message, com.techbank.account.common.events.AccountOpenedEvent.class);
                    eventHandler.on(opened);
                    break;
                case "FundsDepositedEvent":
                    var deposited = objectMapper.convertValue(message, com.techbank.account.common.events.FundsDepositedEvent.class);
                    eventHandler.on(deposited);
                    break;
                case "FundsWithdrawnEvent":
                    var withdrawn = objectMapper.convertValue(message, com.techbank.account.common.events.FundsWithdrawnEvent.class);
                    eventHandler.on(withdrawn);
                    break;
                case "AccountClosedEvent":
                    var closed = objectMapper.convertValue(message, com.techbank.account.common.events.AccountClosedEvent.class);
                    eventHandler.on(closed);
                    break;
                default:
                    logger.log(Level.WARNING, "Unknown eventType received: " + eventType);
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error processing event", e);
        } finally {
            ack.acknowledge();
        }
    }
}
