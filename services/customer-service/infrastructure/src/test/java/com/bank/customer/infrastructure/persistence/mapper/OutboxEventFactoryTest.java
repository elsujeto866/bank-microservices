package com.bank.customer.infrastructure.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.bank.customer.domain.event.CustomerDeactivated;
import com.bank.customer.domain.event.CustomerEvent;
import com.bank.customer.domain.event.CustomerProfileUpdated;
import com.bank.customer.domain.event.CustomerRegistered;
import com.bank.customer.domain.model.Address;
import com.bank.customer.domain.model.CustomerId;
import com.bank.customer.domain.model.Gender;
import com.bank.customer.domain.model.Identification;
import com.bank.customer.domain.model.PersonName;
import com.bank.customer.domain.model.PersonalData;
import com.bank.customer.domain.model.PhoneNumber;
import com.bank.customer.infrastructure.persistence.entity.OutboxEventEntity;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * The translation from domain event to published contract.
 *
 * <p>No Spring context and no database: this class is a pure function from a
 * domain event to a row, so it gets a pure unit test. The container-backed test
 * next door proves the row reaches PostgreSQL; this one proves the row says the
 * right thing.
 */
class OutboxEventFactoryTest {

    private static final Instant OCCURRED = Instant.parse("2026-09-06T14:30:00Z");
    private static final Instant PERSISTED = Instant.parse("2026-09-06T14:30:02Z");
    private static final CustomerId ID = new CustomerId(UUID.fromString("3f1a8d2e-7c44-4b0a-9c6e-1f2b3c4d5e6f"));
    private static final String CORRELATION_ID = "4f2c1a9e8b7d6c5a";

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final OutboxEventFactory factory =
            new OutboxEventFactory(objectMapper, Clock.fixed(PERSISTED, ZoneOffset.UTC));

    private static PersonalData profile() {
        return new PersonalData(
                new PersonName("Jose Lema"),
                Gender.MALE,
                new Identification("1712345678"),
                new Address("Otavalo sn y principal"),
                new PhoneNumber("098254785"));
    }

    @Test
    @DisplayName("publishes CustomerRegistered under the contract name CustomerCreated")
    void translatesEventName() {
        CustomerEvent event = new CustomerRegistered(ID, profile(), true, OCCURRED);

        OutboxEventEntity row = factory.from(event, CORRELATION_ID);

        // The domain and the published contract are allowed to disagree, and
        // this is where they are reconciled. Renaming the domain class must not
        // break a consumer of customer.events.v1.
        assertThat(row.getEventType()).isEqualTo("CustomerCreated");
        assertThat(row.getPayload()).contains("\"eventType\":\"CustomerCreated\"");
    }

    @Test
    @DisplayName("publishes CustomerProfileUpdated under the contract name CustomerUpdated")
    void translatesUpdateName() {
        OutboxEventEntity row = factory.from(new CustomerProfileUpdated(ID, profile(), true, OCCURRED), CORRELATION_ID);

        assertThat(row.getEventType()).isEqualTo("CustomerUpdated");
    }

    @Test
    @DisplayName("keeps occurredAt from the domain and stamps createdAt from the clock")
    void keepsBothTimestamps() {
        OutboxEventEntity row = factory.from(new CustomerRegistered(ID, profile(), true, OCCURRED), CORRELATION_ID);

        // Two different timestamps that are easy to conflate. occurredAt is when
        // the business fact happened; createdAt is when the row was written.
        // Using one for the other silently corrupts anything time-based
        // downstream.
        assertThat(row.getOccurredAt()).isEqualTo(OCCURRED);
        assertThat(row.getCreatedAt()).isEqualTo(PERSISTED);
    }

    @Test
    @DisplayName("keys the row by customer id, which is the Kafka partition key")
    void keysByAggregate() {
        OutboxEventEntity row = factory.from(new CustomerRegistered(ID, profile(), true, OCCURRED), CORRELATION_ID);

        // Kafka orders within a partition, so all events for one customer must
        // hash to one partition. That guarantee starts with this field.
        assertThat(row.getAggregateId()).isEqualTo(ID.value());
        assertThat(row.getAggregateType()).isEqualTo("Customer");
    }

    @Test
    @DisplayName("gives every event a distinct id, which is what consumers deduplicate on")
    void generatesDistinctEventIds() {
        OutboxEventEntity first = factory.from(new CustomerRegistered(ID, profile(), true, OCCURRED), CORRELATION_ID);
        OutboxEventEntity second = factory.from(new CustomerRegistered(ID, profile(), true, OCCURRED), CORRELATION_ID);

        // Kafka delivery is at-least-once, so duplicates will arrive. The id is
        // how a consumer tells "the same event again" from "a second event that
        // happens to look identical".
        assertThat(first.getId()).isNotEqualTo(second.getId());
        assertThat(first.getPayload()).contains(first.getId().toString());
    }

    @Test
    @DisplayName("carries the full profile snapshot, so a consumer never has to call back")
    void carriesSnapshot() {
        OutboxEventEntity row = factory.from(new CustomerRegistered(ID, profile(), true, OCCURRED), CORRELATION_ID);

        assertThat(row.getPayload())
                .contains("\"name\":\"Jose Lema\"")
                .contains("\"identification\":\"1712345678\"")
                .contains("\"address\":\"Otavalo sn y principal\"")
                .contains("\"phone\":\"098254785\"")
                .contains("\"status\":true");
    }

    @Test
    @DisplayName("never puts a credential on the topic")
    void neverLeaksCredentials() {
        OutboxEventEntity row = factory.from(new CustomerRegistered(ID, profile(), true, OCCURRED), CORRELATION_ID);

        // Structurally impossible rather than merely absent: PersonalData does
        // not carry a password, so there is no field to forget to exclude.
        // The test exists to catch somebody adding one later.
        assertThat(row.getPayload()).doesNotContainIgnoringCase("password").doesNotContain("$2a$");
    }

    @Test
    @DisplayName("deactivation carries only the id and the timestamp")
    void deactivationPayloadIsMinimal() {
        OutboxEventEntity row = factory.from(new CustomerDeactivated(ID, OCCURRED), CORRELATION_ID);

        assertThat(row.getEventType()).isEqualTo("CustomerDeactivated");
        assertThat(row.getPayload())
                .contains("\"deactivatedAt\":\"2026-09-06T14:30:00Z\"")
                .doesNotContain("\"name\"");
    }

    @Test
    @DisplayName("records the correlation id so a failure can be traced across services")
    void recordsCorrelationId() {
        OutboxEventEntity row = factory.from(new CustomerRegistered(ID, profile(), true, OCCURRED), CORRELATION_ID);

        assertThat(row.getCorrelationId()).isEqualTo(CORRELATION_ID);
    }

    @Test
    @DisplayName("leaves publishedAt null, marking the row as pending for the relay")
    void startsUnpublished() {
        OutboxEventEntity row = factory.from(new CustomerRegistered(ID, profile(), true, OCCURRED), CORRELATION_ID);

        // The partial index the relay queries covers exactly these rows.
        assertThat(row.getPublishedAt()).isNull();
    }
}
