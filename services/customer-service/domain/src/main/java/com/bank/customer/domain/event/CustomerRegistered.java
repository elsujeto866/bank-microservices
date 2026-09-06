package com.bank.customer.domain.event;

import com.bank.customer.domain.model.CustomerId;
import com.bank.customer.domain.model.PersonalData;
import java.time.Instant;

/**
 * A customer was registered.
 *
 * <p>Carries the full profile rather than only the id. A consumer building a
 * read model can then act on this message alone, with no callback to
 * {@code customer-service} — which is the entire point of publishing an event
 * instead of a notification. An event that says only "customer 3f1a changed"
 * forces every consumer to call back, and re-couples the two services exactly
 * where they were meant to be independent.
 *
 * <p>The password is absent, and always will be. A credential must never leave
 * the service that owns it, least of all onto a retained topic readable by
 * every consumer in the cluster.
 */
public record CustomerRegistered(CustomerId customerId, PersonalData profile, boolean active, Instant occurredAt)
        implements CustomerEvent {}
