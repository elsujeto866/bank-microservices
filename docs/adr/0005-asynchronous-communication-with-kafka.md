# 5. Asynchronous communication between services with Kafka

- **Status:** Accepted
- **Date:** 2026-09-06

## Context

The Senior tier of the exercise requires asynchronous communication between the
two services and asks the solution to address performance, scalability and
resilience.

`account-service` needs to know which customers exist and what their status is.
Fetching that synchronously over REST on every account operation creates
**temporal coupling**: `account-service` is available only while
`customer-service` is available. Two services at 99.9% chained synchronously
give 99.8% — availability multiplies downward, and it does so silently until
the day it doesn't.

## Decision

`customer-service` publishes domain events to Kafka. `account-service` consumes
them and maintains a private read model (ADR-0004).

- **Topics:** `customer.events.v1`, with `customerId` as the partition key.
  Kafka guarantees ordering *within* a partition, so keying by customer means
  all events for one customer are ordered relative to each other — which is the
  only ordering guarantee we actually need.
- **Contract:** event schemas are versioned and specified up front, alongside
  the REST contracts (ADR-0006). The topic name carries the major version.
- **Publication — Transactional Outbox.** A domain change and its event are
  written in the **same database transaction**, the event landing in an
  `outbox` table. A separate relay publishes from that table to Kafka. Writing
  to the database and then publishing to Kafka as two independent operations is
  a dual-write, and a crash between them loses the event permanently. The outbox
  removes that window.
- **Consumption — idempotent, with at-least-once delivery.** Kafka guarantees
  at-least-once, which means duplicates *will* arrive. Every consumer records
  processed event IDs and ignores repeats. Idempotency is not a nicety here; it
  is a correctness requirement.
- **Failure handling:** retry with exponential backoff, then route to a
  dead-letter topic. A poison message must never block its partition forever.

## Consequences

- The services are temporally decoupled: `customer-service` can be down,
  redeploying or slow, and `account-service` keeps serving traffic from its
  local projection.
- Load is absorbed by the broker instead of being propagated as backpressure
  onto a synchronous caller.
- Each service scales independently; consumer throughput scales by adding
  partitions and consumer instances.
- Cost: **eventual consistency becomes a business-visible property.** A customer
  created a moment ago may not yet be known to `account-service`. This must be a
  deliberate, documented behaviour of the API, not a bug report.
- Cost: significant operational surface. Kafka must be run, monitored, and
  reasoned about — consumer lag, rebalances, partition skew, DLQ drainage.
- Cost: debugging spans two services and a broker. Correlation IDs propagated
  through event headers are mandatory, not optional.

## Alternatives considered

- **Synchronous REST** (the SemiSenior tier). Simpler and strongly consistent,
  but temporally coupled. Rejected by the requirement and by the availability
  argument above.
- **RabbitMQ / SQS.** Both are valid asynchronous transports. Kafka was chosen
  for its retained, replayable log: a new consumer — or a rebuilt read model —
  can replay history from offset zero. With a queue, a consumed message is gone.
  That replay property is what makes the read model in ADR-0004 recoverable.
- **Change Data Capture (Debezium) instead of an outbox relay.** Strictly better
  at scale, and it removes the relay entirely. Rejected as disproportionate
  operational weight for this exercise; the outbox table gives the same
  correctness guarantee with far less machinery.
