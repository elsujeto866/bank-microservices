package com.bank.customer.application.port.in;

import com.bank.customer.domain.model.CustomerId;
import reactor.core.publisher.Mono;

/**
 * Inbound port: deactivate a customer.
 *
 * <p>Returns {@code Mono<Void>} — the endpoint answers 204 with no body, and a
 * port that returned the aggregate would tempt somebody to serialise it.
 */
public interface DeactivateCustomer {

    Mono<Void> deactivate(CustomerId customerId);
}
