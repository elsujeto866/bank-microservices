# Contracts

**These files are the source of truth for every interface this system exposes.**

Not the controllers. Not the DTOs. These.

```
contracts/
├── customer-service/openapi.yaml            REST API of customer-service
├── account-service/openapi.yaml             REST API of account-service
└── events/customer-events.asyncapi.yaml     Kafka topic customer.events.v1
```

## How the contract reaches the code

```
contracts/customer-service/openapi.yaml
              │
              │  ./gradlew openApiGenerate
              ▼
build/generated/openapi/…/api/CustomersApi.java      ← interface, one per tag
build/generated/openapi/…/model/*.java               ← request/response DTOs
              │
              │  implements
              ▼
CustomerController.java                              ← handwritten, in main/
```

`CustomerController implements CustomersApi`. Change a path, a status code or a
response type in the YAML without updating the controller and the module stops
compiling. The contract is enforced by the compiler, not by a reviewer's memory.

Generated code is written under `build/` and **is never committed**. It is a
derivative, regenerated on every build. If you want to change the API, change
the YAML.

## Rules

1. **Edit the specification first.** Always. Writing the controller and
   back-filling the YAML is code-first wearing a contract-first costume.
2. **Never edit generated files.** They are overwritten on the next build, and
   they live outside `src/main` precisely so this is hard to do by accident.
3. **A breaking change needs a new major version.** New optional field: fine.
   Removed field, renamed field, changed type, tightened validation: that is a
   `v2`, published alongside `v1` until every consumer has moved.
4. **Events are an API too.** `customer.events.v1` has consumers you do not
   control and messages that outlive your deployment. It gets the same care as
   the REST surface, which is why the AsyncAPI document exists.

## Decisions worth knowing before reading the YAML

| Decision | Where | Why |
|---|---|---|
| Money is a **string**, never a JSON number | `MonetaryAmount` | JSON numbers are IEEE-754 doubles in most parsers. `0.1 + 0.2 = 0.30000000000000004`. A string crosses the wire exactly and becomes a `BigDecimal`-backed `Money` at the boundary |
| Identifiers are **UUIDs** | everywhere | Sequential ids are enumerable, leak record counts, and couple the public contract to the storage engine |
| Errors are **RFC 9457 Problem Details** | `Problem` | One error shape platform-wide. `type` is the machine-readable key; `detail` is prose and must never be parsed |
| `400` vs `422` | responses | `400` = "I could not read this". `422` = "I read it fine, and the answer is no" |
| Collections are **always paginated**, capped at 100 | `page`, `size` | An unbounded list endpoint is a denial-of-service vector against your own database |
| `Idempotency-Key` on every `POST` | parameters | `POST` is not idempotent. A client that times out cannot know whether the write happened; without this header its only choices are a duplicate or a lost write |
| `password` is `writeOnly` | `CreateCustomerRequest` | An input-only field belongs in the request schema alone. Sharing one model between request and response is how credentials reach a log line |
| The statement report negotiates on **`Accept`** | `/reports/{clientId}` | JSON and Excel are two renderings of one resource, not two resources. That is what content negotiation is for |
| `/reports/{clientId}` is **not** under `/api/v1` | `/reports/{clientId}` | The exercise pins that path. Deliberate compliance, not an oversight |

## Working with these files

```bash
./gradlew openApiGenerate         # regenerate both services
./gradlew build                   # generation runs as part of the build
```

`validateSpec` is on: an invalid document fails the build rather than quietly
generating something subtly wrong.

To read them as rendered documentation, paste a file into
[editor.swagger.io](https://editor.swagger.io) (OpenAPI) or
[studio.asyncapi.com](https://studio.asyncapi.com) (AsyncAPI).
