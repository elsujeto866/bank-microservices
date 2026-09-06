package com.bank.customer.domain.event;

import com.bank.customer.domain.model.CustomerId;
import java.time.Instant;

/**
 * Something that happened to a customer, stated as a fact in the past tense.
 *
 * <p>A {@code sealed} interface: the set of things that can happen to a
 * customer is closed and known here. That lets a consumer {@code switch} over
 * events with the compiler checking exhaustiveness — add a new event type and
 * every switch that forgot to handle it stops compiling. An open hierarchy
 * gives you a {@code default} branch instead, and a {@code default} branch is
 * where forgotten cases go to be silently ignored.
 *
 * <p>Note what is <em>not</em> here: no {@code eventId}, no schema version, no
 * Kafka headers, no serialisation format. Those belong to the envelope the
 * outbox writes, and the envelope is an infrastructure concern. This is the
 * business fact; how it is shipped is somebody else's problem.
 */
public sealed interface CustomerEvent permits CustomerRegistered, CustomerProfileUpdated, CustomerDeactivated {

    CustomerId customerId();

    /**
     * When the fact occurred in the domain — not when it was published, and not
     * when it was consumed. Those three timestamps differ, and using the wrong
     * one silently corrupts anything time-based downstream.
     */
    Instant occurredAt();
}
