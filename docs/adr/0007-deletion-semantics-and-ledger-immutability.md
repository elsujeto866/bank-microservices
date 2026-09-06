# 7. Deletion semantics and ledger immutability

- **Status:** Accepted
- **Date:** 2026-09-06

## Context

F1 of the exercise requires full CRUD — "create, edit, update and **delete**
records" — for Customer, Account and Movement.

Taken literally on a banking domain, two of those three deletions are
professionally wrong:

- **Deleting a customer** who owns accounts either orphans that financial
  history or cascades it into oblivion. Either way, an auditor asking "who
  moved this money" gets no answer.
- **Deleting or editing a movement** rewrites financial history. Every
  statement already produced from that account stops reconciling, and there is
  no record that anything changed. A ledger whose past can be altered is not a
  ledger.

The counter-pressure is real: the exercise is graded against its stated
requirements, and an endpoint that is missing because the implementer disagreed
with the brief is a failed requirement, not a principled stand.

## Decision

**Implement the full CRUD surface the exercise specifies, and make each
deletion mean the safest thing that verb can honestly mean.**

| Operation | Behaviour | HTTP |
|---|---|---|
| `DELETE /api/v1/customers/{id}` | Deactivates the customer (`status = false`), emits `CustomerDeactivated`. Row retained. | `204`, or `409` if active accounts still hold a balance |
| `DELETE /api/v1/accounts/{id}` | Closes the account. Row and movements retained. | `204`, or `409` if the balance is non-zero |
| `DELETE /api/v1/movements/{id}` | Reverses the movement's effect and recomputes the balances of every later movement on that account. | `204`, or `422` if the reversal would drive the account below zero |
| `PUT /api/v1/movements/{id}` | Corrects the movement and recomputes subsequent balances. | `200`, or `422` on the same condition |

Every endpoint the exercise asks for exists and behaves predictably. The verb
is honoured; the data is not destroyed.

**What a production system would do instead**, recorded here because it is the
part worth defending in the interview: the ledger would be strictly
append-only. A wrong movement is not edited or removed — a *compensating*
movement is posted that cancels it, and both entries remain visible. The
account balance still ends up correct, the original fact is still on record,
and the correction itself is auditable. `PUT` and `DELETE` on a movement would
then be implemented as "post the reversal", returning the compensating entry
rather than pretending the original never happened.

That design is not implemented here because it changes the shape of the API the
exercise specifies. The decision is deliberate, and it is written down rather
than left as a surprise.

## Consequences

- Every F1 requirement is met, with the exact verbs and paths requested.
- No financial record is ever physically destroyed by an API call.
- `DELETE` and `PUT` on a movement must recompute `balanceAfter` for every
  later movement on the account. That is an O(n) write inside a transaction and
  it must take a row lock on the account — otherwise two concurrent
  corrections interleave and the running balance ends up wrong. This is called
  out now because it is easy to miss and expensive to discover later.
- Deactivation is not deletion, so `GET` on a deactivated customer still
  returns `200` with `status: false`. Clients must filter on `status`, and the
  list endpoints expose that filter for exactly this reason.
- Cost: a reviewer skimming for a hard `DELETE` will not find one. That is why
  the behaviour is documented in the OpenAPI description of every endpoint, not
  only here.

## Alternatives considered

- **Hard delete, as the word literally reads.** Simplest to implement and
  simplest to demo. Rejected: it destroys auditable financial history, and
  defending it in a banking interview is not a position worth taking.
- **Refuse to expose `DELETE` on movements at all** and provide only a
  `POST /movements/{id}/reversal`. Architecturally the better answer, and the
  one a production system should use. Rejected here because it does not deliver
  the CRUD surface the exercise explicitly requires — the concern is recorded
  above instead of being silently substituted for the requirement.
