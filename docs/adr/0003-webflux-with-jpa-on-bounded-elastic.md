# 3. WebFlux with JPA offloaded to a bounded elastic scheduler

- **Status:** Accepted
- **Date:** 2026-09-06

## Context

The exercise mandates two requirements that are in direct technical conflict:

- *"The data layer must be handled with JPA."*
- *"Use Spring WebFlux."*

JPA is a **blocking** API. It sits on JDBC, which parks the calling thread on a
socket read until the database answers. WebFlux runs on Netty with an event loop
of roughly one thread per CPU core, and those threads serve *every* in-flight
request. Blocking one does not cost one request — it costs a fraction of the
entire server for the duration of the call. Block all of them and the
application stops responding while the CPU sits near idle.

Naively wrapping JPA calls in `Mono.fromCallable` without changing the execution
context is the worst of both worlds: all the complexity of reactive code and
*worse* throughput than Spring MVC, which at least has a large servlet thread
pool built for blocking work.

## Decision

Use Spring WebFlux as the web stack, keep JPA as the persistence technology, and
execute every blocking persistence call on `Schedulers.boundedElastic()`:

```java
Mono.fromCallable(() -> repository.findById(id))
    .subscribeOn(Schedulers.boundedElastic());
```

This does not make JPA non-blocking — nothing can. It **relocates** the blocking
call from the event loop to a sacrificial worker thread. The event loop is
released immediately and stays available to the other in-flight connections.

Two operational constraints follow, and they are the point of this ADR:

1. **The scheduler pool and the HikariCP pool must be sized together.** The
   default `boundedElastic` cap is `10 × cores`. If eighty worker threads
   contend for ten JDBC connections, seventy of them block waiting for a
   *connection* instead of a query. The bottleneck moves; it does not disappear.
   Both pools are configured explicitly rather than left at their defaults.
2. **A transaction is bound to a thread.** Spring's `@Transactional` stores its
   context in a `ThreadLocal`. The entire transactional unit of work must
   therefore execute on one worker thread — the `@Transactional` service method
   is wrapped as a whole, never split across operators.

`boundedElastic` is *bounded* by design: it caps thread creation and rejects
work with `RejectedExecutionException` once the queue is exhausted. Under
overload the service fails fast and observably instead of allocating threads
until the JVM dies. That is the resilience property the exercise asks us to
contemplate.

## Consequences

- Both stated requirements are satisfied literally.
- The database path's throughput ceiling is the pool size — the same ceiling
  Spring MVC would have. **We gain no database throughput from this choice.**
- We do gain everything else: the event loop never stalls, so Kafka consumption,
  outbound `WebClient` calls and the streamed statement report are genuinely
  reactive, and the service degrades gracefully under load rather than freezing.
- Cost: reactive code is harder to read, harder to debug (stack traces are
  fragmented; `checkpoint()` and `onErrorMap` become necessary), and easy to get
  subtly wrong. Every blocking call must be wrapped — one that is missed is
  invisible until it is under load.
- `BlockHound` is a natural follow-up: it detects blocking calls on event-loop
  threads at test time.

## Alternatives considered

- **WebFlux + R2DBC.** Genuinely reactive end to end, no thread offloading, no
  pool alignment problem. Rejected: it violates the explicit JPA requirement,
  and it gives up JPA's mapping, relationship and transaction management for a
  domain that is relational by nature.
- **Spring MVC + JPA.** The simplest correct combination, and on Java 21 with
  virtual threads it would rival WebFlux for I/O-bound work. Rejected: it
  violates the explicit WebFlux requirement.
- **WebFlux + JPA with no scheduler offloading.** Rejected: strictly worse than
  every option above.
