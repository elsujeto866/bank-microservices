-- =============================================================================
-- BaseDatos.sql
--
-- Deliverable required by the exercise: the full database script for the
-- solution.
--
-- IMPORTANT — this file is a convenience copy, not the source of truth.
--
-- The schema each service actually applies lives in its own Flyway migrations:
--
--     services/customer-service/infrastructure/src/main/resources/db/migration/
--     services/account-service/infrastructure/src/main/resources/db/migration/
--
-- Those run automatically on startup, are versioned, are checksum-validated on
-- every boot, and are what Hibernate validates its mappings against
-- (ddl-auto=validate). Running this file by hand is never necessary and is
-- offered only because the exercise asks for a single script.
--
-- Each service owns a PRIVATE database (ADR-0004). The two sections below are
-- deliberately NOT meant to be applied to the same database — doing so would
-- recreate the shared-schema anti-pattern the architecture exists to avoid, and
-- would let somebody write a JOIN across a service boundary.
--
--     psql -h localhost -p 5433 -U customer_user -d customer_db  -- section 1
--     psql -h localhost -p 5434 -U account_user  -d account_db   -- section 2
-- =============================================================================


-- #############################################################################
-- SECTION 1 — customer_db  (owned by customer-service)
--
-- Source: services/customer-service/infrastructure/src/main/resources/
--         db/migration/V1__create_customer_schema.sql
-- #############################################################################

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

-- The authoritative guard against duplicate customers. The use case also checks
-- before inserting, but only for a clear error message: between that check and
-- the insert another request can commit, and only the database sees both writes.
ALTER TABLE customers
    ADD CONSTRAINT uk_customers_identification UNIQUE (identification);

-- Partial: inactive customers are a small minority, and a smaller index is a
-- faster index that stays in memory.
CREATE INDEX ix_customers_active ON customers (active) WHERE active = TRUE;

-- Paginated listings need a deterministic order. PostgreSQL makes no promise
-- about row order without ORDER BY, so the same row can appear on two pages.
CREATE INDEX ix_customers_created_at ON customers (created_at);

COMMENT ON TABLE  customers IS 'Customers of the bank. Rows are deactivated, never deleted (ADR-0007).';
COMMENT ON COLUMN customers.identification IS 'National identification number. Natural key, stored upper-cased.';
COMMENT ON COLUMN customers.password_hash IS 'BCrypt hash. The plaintext never reaches this database.';
COMMENT ON COLUMN customers.version IS 'Optimistic lock. Incremented by Hibernate on every update.';


-- Transactional outbox (ADR-0005).
--
-- Written in the SAME transaction as the state change it describes. Saving the
-- customer and publishing to Kafka as two independent operations is a dual
-- write: a crash between them loses the event with no trace it was ever owed.
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

-- The relay's only query. Partial on published_at IS NULL, so the index holds
-- just the handful of pending rows no matter how large the table grows — the
-- relay stays the same speed on day one and on day one thousand.
CREATE INDEX ix_outbox_pending
    ON outbox_events (occurred_at)
    WHERE published_at IS NULL;

CREATE INDEX ix_outbox_published_at ON outbox_events (published_at);
CREATE INDEX ix_outbox_aggregate ON outbox_events (aggregate_id, occurred_at);

COMMENT ON TABLE  outbox_events IS 'Transactional outbox. Written atomically with the state change; drained by the relay (ADR-0005).';
COMMENT ON COLUMN outbox_events.id IS 'Event identity and the key consumers deduplicate on. Kafka delivery is at-least-once.';
COMMENT ON COLUMN outbox_events.aggregate_id IS 'Kafka partition key. Guarantees per-customer ordering.';
COMMENT ON COLUMN outbox_events.occurred_at IS 'When the fact occurred in the domain, not when it was published.';
COMMENT ON COLUMN outbox_events.published_at IS 'NULL means pending. Rows are marked, never deleted by the relay.';


-- #############################################################################
-- SECTION 2 — account_db  (owned by account-service)
--
-- Arrives with the account-service stage. Kept as a placeholder so this file's
-- structure matches the final deliverable rather than being restructured later.
-- #############################################################################

-- Pending: accounts, movements, processed_events (consumer deduplication),
-- customer_projection (the local read model built from customer.events.v1).
