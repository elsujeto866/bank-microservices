package com.bank.customer.infrastructure.persistence.mapper;

import com.bank.customer.domain.event.CustomerDeactivated;
import com.bank.customer.domain.event.CustomerEvent;
import com.bank.customer.domain.event.CustomerProfileUpdated;
import com.bank.customer.domain.event.CustomerRegistered;
import com.bank.customer.domain.model.CustomerId;
import com.bank.customer.domain.model.PersonalData;
import com.bank.customer.infrastructure.persistence.entity.OutboxEventEntity;
// Jackson 3 (shipped with Spring Boot 4) lives under tools.jackson, not
// com.fasterxml.jackson. The annotations kept the old package; the databind
// classes did not. Nearly every Jackson snippet online still shows the Jackson 2
// import, and it will not resolve here.
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Turns a domain event into the outbox row that will be published to Kafka.
 *
 * <p>This class is the boundary between two vocabularies. The domain calls it
 * {@code CustomerRegistered} because that is what happened in the business.
 * The published contract calls it {@code CustomerCreated} because that is what
 * {@code customer.events.v1} promised its consumers. Renaming a domain class
 * must not break a consumer, and a consumer's naming must not constrain the
 * model — so the translation happens here, once, explicitly.
 */
@Component
public class OutboxEventFactory {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventFactory.class);

    private static final String AGGREGATE_TYPE = "Customer";
    private static final String SCHEMA_VERSION = "1.0";

    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OutboxEventFactory(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public OutboxEventEntity from(CustomerEvent event, String correlationId) {
        // An exhaustive switch over a sealed interface. There is no `default`
        // branch, and that is the entire benefit: add a fourth event type to the
        // domain and this method stops compiling until it is handled. With a
        // `default`, the new event would silently serialise as whatever the
        // fallback produced, and the bug would surface in a consumer.
        String contractEventType = switch (event) {
            case CustomerRegistered ignored -> "CustomerCreated";
            case CustomerProfileUpdated ignored -> "CustomerUpdated";
            case CustomerDeactivated ignored -> "CustomerDeactivated";
        };

        ObjectNode envelope = objectMapper.createObjectNode();
        UUID eventId = UUID.randomUUID();

        envelope.put("eventId", eventId.toString());
        envelope.put("eventType", contractEventType);
        envelope.put("occurredAt", event.occurredAt().toString());
        envelope.put("customerId", event.customerId().value().toString());
        envelope.put("schemaVersion", SCHEMA_VERSION);
        envelope.set("data", dataOf(event));

        OutboxEventEntity entity = new OutboxEventEntity();
        entity.setId(eventId);
        entity.setAggregateId(event.customerId().value());
        entity.setAggregateType(AGGREGATE_TYPE);
        entity.setEventType(contractEventType);
        entity.setPayload(envelope.toString());
        entity.setOccurredAt(event.occurredAt());
        entity.setCreatedAt(clock.instant());
        entity.setCorrelationId(correlationId);

        log.debug(
                "Queued outbox event type={} eventId={} customerId={} correlationId={}",
                contractEventType,
                eventId,
                event.customerId(),
                correlationId);

        return entity;
    }

    private ObjectNode dataOf(CustomerEvent event) {
        return switch (event) {
            case CustomerRegistered registered ->
                    snapshot(registered.customerId(), registered.profile(), registered.active());
            case CustomerProfileUpdated updated ->
                    snapshot(updated.customerId(), updated.profile(), updated.active());
            case CustomerDeactivated deactivated -> {
                ObjectNode node = objectMapper.createObjectNode();
                node.put("customerId", deactivated.customerId().value().toString());
                node.put("deactivatedAt", deactivated.occurredAt().toString());
                yield node;
            }
        };
    }

    /**
     * Builds the snapshot payload the AsyncAPI document specifies.
     *
     * <p>The password is not here, and there is no code path that could put it
     * here: {@link PersonalData} does not carry one. A credential must never
     * leave the service that owns it, least of all onto a retained topic
     * readable by every consumer in the cluster — and the safest way to
     * guarantee that is for the object being serialised to have nothing to
     * leak.
     */
    private ObjectNode snapshot(CustomerId customerId, PersonalData profile, boolean active) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("customerId", customerId.value().toString());
        node.put("name", profile.name().value());
        node.put("gender", profile.gender().name());
        node.put("identification", profile.identification().value());
        node.put("address", profile.address().value());
        node.put("phone", profile.phone().value());
        node.put("status", active);
        return node;
    }
}
