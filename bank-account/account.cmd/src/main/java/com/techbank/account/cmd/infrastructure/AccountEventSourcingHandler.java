package com.techbank.account.cmd.infrastructure;

import com.techbank.account.cmd.domain.AccountAggregate;
import com.techbank.cqrs.core.domain.AggregateRoot;
import com.techbank.cqrs.core.handlers.EventSourcingHandler;
import com.techbank.cqrs.core.infrastructure.EventStore;
import com.techbank.cqrs.core.producers.EventProducer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Comparator;

@Service
public class AccountEventSourcingHandler implements EventSourcingHandler<AccountAggregate> {
    @Autowired
    private EventStore eventStore;

    @Autowired
    private EventProducer eventProducer;

    @Autowired
    private org.springframework.kafka.core.KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void save(AggregateRoot aggregate) {
        eventStore.saveEvents(aggregate.getId(), aggregate.getUncommittedChanges(), aggregate.getVersion());
        aggregate.markChangesAsCommitted();
    }

    @Override
    public AccountAggregate getById(String id) {
        var aggregate = new AccountAggregate();
        var events = eventStore.getEvents(id);
        if (events != null && !events.isEmpty()) {
            aggregate.replayEvents(events);
            var latestVersion = events.stream().map(x -> x.getVersion()).max(Comparator.naturalOrder());
            aggregate.setVersion(latestVersion.get());
        }
        return aggregate;
    }

    @Override
    public void republishEvents() {
        var aggregateIds = eventStore.getAggregateIds();
        for(var aggregateId: aggregateIds) {
            var aggregate = getById(aggregateId);
            if (aggregate == null || !aggregate.getActive()) continue;
            var events = eventStore.getEvents(aggregateId);
            for(var event: events) {
                var topic = "account-events";
                org.apache.kafka.clients.producer.ProducerRecord<String, Object> record = new org.apache.kafka.clients.producer.ProducerRecord<>(topic, aggregateId, event);
                record.headers().add("eventType", event.getClass().getSimpleName().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                kafkaTemplate.send(record);
            }
        }
    }
}
