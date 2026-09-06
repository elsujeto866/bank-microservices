package com.bank.customer.domain.event;

import com.bank.customer.domain.model.CustomerId;
import java.time.Instant;

/**
 * A customer was deactivated.
 *
 * <p>A state change, not a tombstone. The record is retained — accounts and
 * movements refer to this customer and that history must stay auditable
 * (ADR-0007).
 */
public record CustomerDeactivated(CustomerId customerId, Instant occurredAt) implements CustomerEvent {}
