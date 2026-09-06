# 4. One database per service

- **Status:** Accepted
- **Date:** 2026-09-06

## Context

The exercise splits the system into two services: `customer-service`
(Customer, Person) and `account-service` (Account, Movement). The obvious
shortcut is to point both at one schema — then `account-service` can simply
`JOIN` on the customer table to validate that a customer exists.

That shortcut has a name: the shared database anti-pattern. Two services writing
one schema are not two services. They are one deployment unit wearing a costume,
because neither can migrate a table, change a column type or deploy
independently without coordinating with the other.

## Decision

Each service owns a **private** PostgreSQL database. No service reads another
service's tables — not through a JOIN, not through a view, not through a
read-only user.

`account-service` needs customer data to validate accounts and to build the
statement report. It obtains that data by consuming customer events from Kafka
(see ADR-0005) and maintaining its own **local read model**: a small, private
projection holding only the customer fields it actually needs.

## Consequences

- Each service can migrate its schema and deploy on its own cadence.
- Each database can be scaled, tuned and indexed for its own access pattern.
- The blast radius of a slow query or a lock is one service.
- Cost: **data is eventually consistent.** For a short window after a customer
  is updated, `account-service` holds a stale copy. This is acceptable here —
  and the business rules were chosen so that no *money* decision depends on the
  replicated data. Balance validation reads only `account-service`'s own tables,
  which are strongly consistent.
- Cost: the customer projection is duplicated storage that must be kept correct.
  Idempotent consumers and event ordering per customer key are mandatory, not
  optional.

## Alternatives considered

- **Shared schema.** Rejected, see Context.
- **Synchronous REST lookup on every operation.** This is what the SemiSenior
  tier of the exercise asks for. Rejected at Senior tier: it couples
  availability (if `customer-service` is down, no account operation succeeds),
  adds a network round trip to the hot path, and is explicitly superseded by the
  asynchronous-communication requirement.
