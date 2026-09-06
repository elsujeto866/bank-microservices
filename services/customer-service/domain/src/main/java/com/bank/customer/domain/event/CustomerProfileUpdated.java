package com.bank.customer.domain.event;

import com.bank.customer.domain.model.CustomerId;
import com.bank.customer.domain.model.PersonalData;
import java.time.Instant;

/**
 * A customer's profile changed.
 *
 * <p>Carries the complete new state, not a delta of what changed.
 *
 * <p>A delta forces every consumer to already hold the previous state and to
 * have applied every earlier event, in order, without ever missing one. A
 * snapshot is self-contained: a consumer that dropped a message, or that is
 * rebuilding from offset zero, converges on the correct answer regardless.
 * Slightly larger messages, dramatically simpler and more robust consumers.
 */
public record CustomerProfileUpdated(CustomerId customerId, PersonalData profile, boolean active, Instant occurredAt)
        implements CustomerEvent {}
