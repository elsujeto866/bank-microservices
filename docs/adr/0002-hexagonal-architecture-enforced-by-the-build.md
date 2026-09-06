# 2. Hexagonal architecture enforced by the build

- **Status:** Accepted
- **Date:** 2026-09-06

## Context

The exercise requires "a clean architecture". In most submissions this means
three packages named `domain`, `application` and `infrastructure` inside a single
compilation unit — and one `@Autowired` in a domain class quietly undoes all of
it. A layering that only exists as a folder convention is enforced by nothing but
good intentions, and good intentions lose to a deadline.

A second force: this system is inherently I/O-bound and event-driven. Persistence
is JPA today and could be R2DBC tomorrow; communication is Kafka today and could
be SQS tomorrow. The business rules — "a debit may not overdraw an account" —
must survive both changes untouched.

## Decision

Each service is split into three **separate Gradle modules** that mirror the
hexagon:

| Module | Contains | May depend on |
|---|---|---|
| `domain` | Entities, value objects, domain services, domain exceptions | nothing |
| `application` | Use cases, input/output ports | `domain` |
| `infrastructure` | REST, JPA, Kafka, Spring configuration, main class | `application` |

Dependencies point strictly inward. Because the modules are distinct compilation
units, an inward-pointing violation is a **compiler error**, not a review
comment.

Two further guards run in `./gradlew check`:

1. **`verifyDomainPurity` / `verifyApplicationPurity`** — Gradle tasks that
   resolve the module's runtime classpath and fail if a framework artifact
   (Spring, Hibernate, Jakarta Persistence, Kafka, Jackson) leaked in.
2. **ArchUnit** — covers what the module graph cannot see: package naming, no
   cycles, annotation placement.

Lombok is the single deliberate exception in the domain. It is declared
`compileOnly`, so it disappears at runtime and the purity check still passes.

## Consequences

- The dependency rule is now mechanical. Nobody has to remember it.
- Swapping a persistence or messaging technology touches `infrastructure` only.
- The domain is unit-testable with a bare JVM: no Spring context, no database,
  no Testcontainers. Tests run in milliseconds, so they actually get run.
- Cost: six Gradle modules instead of two. More build files, a slower cold
  build, and a steeper first read of the repository.
- Cost: mapping between layers (domain model ↔ JPA entity ↔ API DTO) is explicit
  and must be written. That duplication is intentional — it is what keeps a
  database column rename from reaching the public API.

## Alternatives considered

- **Package-only layering in one module.** Cheaper to set up, enforces nothing.
  Rejected: the exercise is judged on architecture, and this variant cannot
  prove it holds.
- **ArchUnit alone, single module.** Catches violations, but only after they are
  written and only if the test is kept green. Module boundaries prevent them
  from compiling at all. We use ArchUnit as a complement, not a substitute.
