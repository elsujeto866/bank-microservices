-- =============================================================================
-- V1 — customer-service schema.
--
-- Flyway migration. The schema is versioned code that ships with the
-- application, not something applied by hand against production on a Friday.
--
-- Hibernate runs with ddl-auto=validate: it checks that the mapping and this
-- schema agree, and refuses to start if they do not. It never creates or alters
-- a table. `ddl-auto=update` in production is how a column quietly disappears.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- customers
-- -----------------------------------------------------------------------------
CREATE TABLE customers (
    id              UUID         PRIMARY KEY,
    name            VARCHAR(120) NOT NULL,
    gender          VARCHAR(20)  NOT NULL,
    identification  VARCHAR(20)  NOT NULL,
    address         VARCHAR(200) NOT NULL,
    phone           VARCHAR(20)  NOT NULL,
    password_hash   VARCHAR(100) NOT NULL,
    active          BOOLEAN      NOT NULL,
    created_at      TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT ck_customers_gender CHECK (gender IN ('MALE', 'FEMALE', 'OTHER', 'UNSPECIFIED'))
);

-- The authoritative guard against duplicate customers.
--
-- The use case also checks before inserting, but only for a clear error
-- message: between that check and this insert another request can slip in, and
-- only the database sees both writes. Identification is upper-cased by the
-- value object before it ever reaches here, so a plain unique index is enough —
-- no LOWER() expression index, and therefore no index the planner might skip.
ALTER TABLE customers
    ADD CONSTRAINT uk_customers_identification UNIQUE (identification);

-- Supports GET /api/v1/customers?status=… and the common "active customers"
-- listing. Partial, because inactive customers are a small minority and a
-- smaller index is a faster index that stays in memory.
CREATE INDEX ix_customers_active ON customers (active) WHERE active = TRUE;

-- Supports ordering the listing endpoint deterministically. A paginated query
-- with no ORDER BY can return the same row on two different pages — PostgreSQL
-- makes no promise about row order without one.
CREATE INDEX ix_customers_created_at ON customers (created_at);

COMMENT ON TABLE  customers IS 'Customers of the bank. Rows are deactivated, never deleted (ADR-0007).';
COMMENT ON COLUMN customers.identification IS 'National identification number. Natural key, stored upper-cased.';
COMMENT ON COLUMN customers.password_hash IS 'BCrypt hash. The plaintext never reaches this database.';
COMMENT ON COLUMN customers.version IS 'Optimistic lock. Incremented by Hibernate on every update.';

-- -----------------------------------------------------------------------------
-- outbox_events
--
-- Written in the SAME transaction as the state change it describes, which is
-- what removes the dual-write window between the database and Kafka (ADR-0005).
-- -----------------------------------------------------------------------------
CREATE TABLE outbox_events (
    id              UUID         PRIMARY KEY,
    aggregate_id    UUID         NOT NULL,
    aggregate_type  VARCHAR(50)  NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    payload         JSONB        NOT NULL,
    occurred_at     TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    created_at      TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    published_at    TIMESTAMP(6) WITH TIME ZONE,
    correlation_id  VARCHAR(64)
);

-- The relay's only query: "what is still pending, oldest first?".
--
-- Partial on published_at IS NULL, which is the whole point. The table grows
-- without bound as events are published, but this index only ever contains the
-- handful of unpublished rows — so the relay's query stays the same speed on
-- day one and on day one thousand.
CREATE INDEX ix_outbox_pending
    ON outbox_events (occurred_at)
    WHERE published_at IS NULL;

-- Supports pruning published rows past the retention window, and answering
-- "did we ever emit this?" during an incident.
CREATE INDEX ix_outbox_published_at ON outbox_events (published_at);

-- Supports replaying or auditing everything that happened to one customer.
CREATE INDEX ix_outbox_aggregate ON outbox_events (aggregate_id, occurred_at);

COMMENT ON TABLE  outbox_events IS 'Transactional outbox. Written atomically with the state change; drained by the relay (ADR-0005).';
COMMENT ON COLUMN outbox_events.id IS 'Event identity and the key consumers deduplicate on. Kafka delivery is at-least-once.';
COMMENT ON COLUMN outbox_events.aggregate_id IS 'Kafka partition key. Guarantees per-customer ordering.';
COMMENT ON COLUMN outbox_events.occurred_at IS 'When the fact occurred in the domain, not when it was published.';
COMMENT ON COLUMN outbox_events.published_at IS 'NULL means pending. Rows are marked, never deleted by the relay.';
