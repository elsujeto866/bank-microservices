

Unit tests and integration tests are separate tasks on purpose.  needs
nothing but a JVM and runs in milliseconds, so it can run on every save;
 starts containers and takes tens of seconds. Merging them
punishes the fast tests for the slow ones, and a suite that needs Docker to run
at all is a suite people stop running locally.# bank-microservices

Event-driven banking backend built as two independent microservices, developed
as a technical exercise at **Senior** level.

> The exercise statement is `Ejercicio Técnico Backend Java v2 19.pdf`.
> Every architectural decision — and every alternative that was rejected — is
> recorded in [`docs/adr/`](docs/adr/). Start there.

---

## The system in one picture

```
                    ┌───────────────────────┐         ┌───────────────────────┐
                    │   customer-service    │         │    account-service    │
   HTTP  ─────────► │  Customer, Person     │         │  Account, Movement    │
                    │                       │         │                       │
                    │  domain               │         │  domain               │
                    │  application          │         │  application          │
                    │  infrastructure       │         │  infrastructure       │
                    └───────────┬───────────┘         └───────────┬───────────┘
                                │                                 │
                     ┌──────────▼──────────┐          ┌───────────▼─────────┐
                     │    customer_db      │          │     account_db      │
                     │     PostgreSQL      │          │     PostgreSQL      │
                     └──────────┬──────────┘          └───────────▲─────────┘
                                │                                 │
                                │   customer.events.v1            │
                                └────────►  Kafka  ───────────────┘
                                            (async, ADR-0005)
```

Two services, **two private databases**, no shared schema, and no synchronous
call between them. `account-service` learns about customers by consuming events
and keeping its own local read model.

---

## Architecture

Each service is three **separate Gradle modules** shaped like a hexagon:

| Module | Holds | May depend on |
|---|---|---|
| `domain` | Entities, value objects, business rules, domain exceptions | *nothing* |
| `application` | Use cases and the ports they speak through | `domain` |
| `infrastructure` | REST, JPA, Kafka, Spring wiring, main class | `application` |

Dependencies point strictly inward. They are separate compilation units, so a
violation is a **compiler error** — not something a reviewer has to catch.

On top of that, `./gradlew check` runs architectural fitness functions that
resolve each inner module's runtime classpath and fail the build if a framework
leaked in:

```
> Architectural violation in :services:customer-service:domain.
  The domain must stay free of frameworks, but these leaked onto its runtime classpath:
    - org.springframework:spring-context:7.0.9
  Move the offending code to the infrastructure module and talk to the domain through a port.
```

Read [ADR-0002](docs/adr/0002-hexagonal-architecture-enforced-by-the-build.md)
for why the boundary is enforced by the build rather than by convention.

---

## Technology

| Concern | Choice | Rationale |
|---|---|---|
| Language | Java 21 (LTS) | Toolchain-pinned, so every machine and CI build produce identical bytecode |
| Framework | Spring Boot 4.1.1 | Latest stable release |
| Web | Spring WebFlux | Required by the exercise |
| Persistence | JPA / Hibernate on PostgreSQL | Required by the exercise — see the caveat below |
| Messaging | Kafka (KRaft mode) | Retained, replayable log; read models can be rebuilt ([ADR-0005](docs/adr/0005-asynchronous-communication-with-kafka.md)) |
| Build | Gradle 9.7.1 (Groovy DSL) | Convention plugins in `buildSrc/`, version catalog, checksum-pinned wrapper |
| Contracts | OpenAPI 3.0.3 + AsyncAPI 3.0 | Contract-first ([ADR-0006](docs/adr/0006-contract-first-api-design.md)) |

**The WebFlux + JPA caveat.** The exercise mandates both, and they conflict: JPA
is blocking, WebFlux is not. Every blocking persistence call is therefore
offloaded to `Schedulers.boundedElastic()`, which does not make JPA
non-blocking — it moves the block off the Netty event loop so the server stays
responsive. The full reasoning, including how the scheduler and HikariCP pools
must be sized together, is in
[ADR-0003](docs/adr/0003-webflux-with-jpa-on-bounded-elastic.md).

---

## Getting started

### Prerequisites

- **JDK 21** — the build declares a toolchain, so Gradle will locate or
  download one, but a local JDK 21 makes the first build far faster.
- **Docker** with Compose v2.
- No Gradle installation needed. Use the wrapper.

### Bring up the infrastructure

```bash
docker compose up -d          # two PostgreSQL databases + Kafka + topic creation
docker compose ps             # all services should report healthy
```

| Service | Host address | Credentials |
|---|---|---|
| `customer-db` | `localhost:5433` | `customer_user` / `customer_pass` / `customer_db` |
| `account-db` | `localhost:5434` | `account_user` / `account_pass` / `account_db` |
| Kafka (from host) | `localhost:29092` | — |
| Kafka (in network) | `kafka:9092` | — |

Tear it down with `docker compose down -v` (`-v` also drops the volumes).

### Build

```bash
./gradlew build                # compile + unit tests + architecture checks
./gradlew architectureCheck    # fitness functions only
./gradlew integrationTest      # container-backed tests (requires Docker)
```

Unit tests and integration tests are separate tasks on purpose. `test` needs
nothing but a JVM and runs in milliseconds, so it can run on every save;
`integrationTest` starts containers and takes tens of seconds. Merging them
punishes the fast tests for the slow ones — and a suite that needs Docker to
run at all is a suite people stop running locally.

---

## Repository layout

```
bank-microservices/
├── buildSrc/                       Convention plugins — the build's own rules
│   └── src/main/groovy/
│       ├── bank.java-conventions.gradle             Java 21, encoding, tests, Lombok
│       ├── bank.domain-conventions.gradle           + domain purity check
│       ├── bank.application-conventions.gradle      + application purity check
│       ├── bank.infrastructure-conventions.gradle   frameworks allowed here
│       └── bank.openapi-conventions.gradle          contract-first code generation
├── contracts/                      OpenAPI + AsyncAPI specifications
├── docs/adr/                       Architecture Decision Records
├── gradle/libs.versions.toml       Version catalog — one place for every version
├── services/
│   ├── customer-service/{domain,application,infrastructure}/
│   └── account-service/{domain,application,infrastructure}/
├── compose.yaml                    Local infrastructure
└── settings.gradle                 The module graph
```

---

## Delivery plan

Built in reviewable stages, one pull request each.

| # | Stage | Status |
|---|---|---|
| 0 | Foundation: build, module graph, fitness functions, ADRs, local infrastructure | ✅ |
| 1 | Contract-first: OpenAPI + AsyncAPI, code generation | ✅ |
| 1.5 | Migrate the build from the Kotlin DSL to the Groovy DSL | ✅ |
| 2 | `customer-service` — domain model, test-first | ✅ |
| 3 | `customer-service` — use cases and ports, test-first | ✅ |
| 4 | `customer-service` — persistence: JPA, outbox, Flyway, schedulers | ✅ |
| 4.5 | `customer-service` — web: REST controllers, error handling | ⬜ |
| 5 | `account-service` — domain and use cases (TDD), F2/F3 balance rules | ⬜ |
| 6 | `account-service` — adapters | ⬜ |
| 7 | Kafka: events, transactional outbox, idempotent consumer, DLQ | ⬜ |
| 8 | F4 — account statement report (JSON and Excel) | ⬜ |
| 9 | F6 — integration tests with Testcontainers, Karate DSL suite | ⬜ |
| 10 | Resilience and observability: Resilience4j, Actuator, pool sizing, indexes | ⬜ |
| 11 | F7 — Docker images, CI pipeline, mutation testing | ⬜ |

The order is deliberate: **domain first, infrastructure last**. Starting from
JPA entities produces a database schema wearing an architecture costume.
