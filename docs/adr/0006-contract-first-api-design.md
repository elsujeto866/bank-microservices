# 6. Contract-first API and event design

- **Status:** Accepted
- **Date:** 2026-09-06

## Context

The exercise explicitly requires a Contract First (API First) approach and the
delivery of an OpenAPI specification.

The common alternative — code first — annotates controllers and generates the
specification from them. The specification then becomes a *report about the
code* rather than an agreement about the interface. Its consequences are
familiar: the API shape is dictated by whatever the JPA entities happen to look
like, a field rename leaks straight to consumers, and nobody can review the
contract before it is already implemented.

## Decision

The specification is the source of truth and is written **before** the
implementation.

- REST contracts live in `contracts/` as **OpenAPI 3.0.3** documents, one per
  service. 3.1 is the better specification — it finally aligns with JSON Schema
  — but tooling support for it is still uneven: `openapi-generator`, Karate and
  several Postman import paths handle 3.0.x flawlessly and 3.1 partially. A
  contract whose whole purpose is to be consumed by tools is not the place to
  be early. Revisit when the generator's 3.1 support is complete.
- Event contracts live beside them as an AsyncAPI document describing the Kafka
  topics and payloads.
- The build runs `openapi-generator` to produce **server interfaces and DTOs**
  from the specification. Controllers implement a generated interface, so a
  controller that drifts from the contract does not compile.
- Generated code is written to `build/generated/` and is **not** committed. The
  contract is the artifact; the code is a derivative that is regenerated every
  build.
- The API model is deliberately distinct from the domain model and from the JPA
  entities. Mapping between them is explicit.

## Consequences

- The contract can be reviewed, discussed and agreed before a line of logic is
  written. Consumers can start against a mock immediately.
- Drift between documentation and behaviour is structurally impossible: the
  compiler is the check.
- Internal refactoring cannot accidentally change the public API.
- Breaking changes become visible as a diff on a specification file, which is
  exactly where a reviewer will notice them.
- Cost: an extra build step, and generated code that must be kept off the source
  path in the IDE.
- Cost: the mapping layer is real work. It is also the layer that buys the
  decoupling, so it is not overhead — it is the product.

## Alternatives considered

- **Code first with springdoc-openapi.** Faster to start, and perfectly
  reasonable for an internal service. Rejected: the exercise requires API First,
  and the coupling it creates between the persistence model and the public
  contract is exactly what this project is meant to avoid.
