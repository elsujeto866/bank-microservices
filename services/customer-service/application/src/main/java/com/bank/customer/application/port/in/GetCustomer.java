package com.bank.customer.application.port.in;

import com.bank.customer.domain.model.Customer;
import com.bank.customer.domain.model.CustomerId;
import reactor.core.publisher.Mono;

/**
 * Inbound port: fetch one customer.
 *
 * <p>Fails with {@code CustomerNotFoundException} rather than completing empty.
 * A read whose caller must answer 404 wants the failure to be explicit — an
 * empty {@code Mono} silently becomes a 200 with no body if a single
 * {@code switchIfEmpty} is forgotten.
 */
public interface GetCustomer {

    Mono<Customer> byId(CustomerId customerId);
}
