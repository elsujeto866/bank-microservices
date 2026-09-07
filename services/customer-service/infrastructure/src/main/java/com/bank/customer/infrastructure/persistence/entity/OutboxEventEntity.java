package com.bank.customer.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A domain event waiting to be published to Kafka — the transactional outbox
 * (ADR-0005).
 *
 * <p><strong>Why this table exists.</strong> Saving the customer and publishing
 * to Kafka are two separate systems. Do them as two independent operations and
 * there is a window between them:
 *
 * <pre>
 *   save customer  ──✓──  [ crash ]  ──✗──  publish event
 * </pre>
 *
 * <p>The customer exists and nobody downstream will ever hear about it. No
 * error, no retry, no trace that an event was owed. That is a dual write, and
 * no amount of try/catch closes it — the process can die between the two lines.
 *
 * <p>The outbox removes the window by making both writes <em>one</em> write.
 * The customer row and this row are inserted in the same database transaction:
 * either both are committed or neither is. A separate relay then reads this
 * table and publishes to Kafka, marking rows as it goes. If the relay crashes
 * mid-publish it simply republishes on restart — which is exactly why every
 * consumer has to be idempotent, and why {@link #id} is the deduplication key.
 *
 * <p>The relay itself arrives in a later stage. The guarantee starts here,
 * because the guarantee is about the <em>write</em>, not about the delivery.
 */
@Entity
@Table(name = "outbox_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEventEntity {

    /**
     * The event's identity, and the key consumers deduplicate on.
     *
     * <p>Generated here rather than in the domain on purpose. A domain event is
     * the business fact ("this customer was registered"); the id, the schema
     * version and the headers are the envelope it travels in, and the envelope
     * is a delivery concern. Assigning it at insert time also makes it stable
     * for the rest of the row's life, which is what deduplication requires.
     */
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Kafka partition key. Keying by aggregate is what guarantees per-customer ordering. */
    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private UUID aggregateId;

    @Column(name = "aggregate_type", nullable = false, updatable = false, length = 50)
    private String aggregateType;

    @Column(name = "event_type", nullable = false, updatable = false, length = 100)
    private String eventType;

    /**
     * The serialised event envelope, as described by the AsyncAPI document.
     *
     * <p>Stored as {@code jsonb} rather than {@code text}: PostgreSQL then
     * validates the JSON on write, so a malformed payload fails at insert
     * instead of at the consumer, hours later and in another service.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, updatable = false, columnDefinition = "jsonb")
    private String payload;

    /** When the fact occurred in the domain — not when this row was written. */
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * When the relay successfully published this event. {@code null} means
     * pending.
     *
     * <p>Rows are marked rather than deleted. The published history is worth
     * keeping for a while: it answers "did we ever emit this?" during an
     * incident, and it allows a replay. A scheduled job prunes rows older than
     * the retention window — an outbox nobody prunes eventually becomes the
     * largest table in the database.
     */
    @Column(name = "published_at")
    private Instant publishedAt;

    /**
     * Correlation id of the request that produced this event, propagated into
     * the Kafka headers by the relay.
     *
     * <p>Without it, a failure that crosses two services and a broker is three
     * unrelated log files.
     */
    @Column(name = "correlation_id", length = 64)
    private String correlationId;
}
