package com.bank.customer.application.port.in;

import com.bank.customer.application.command.RegisterCustomerCommand;
import com.bank.customer.domain.model.Customer;
import reactor.core.publisher.Mono;

/**
 * Inbound port: register a new customer.
 *
 * <p>Inbound ports are the driving side of the hexagon — the complete list of
 * things this service can be asked to do. A controller, a Kafka consumer or a
 * scheduled job all depend on this interface, never on the class behind it.
 *
 * <p>The usual objection is fair, so here it is answered rather than dodged: a
 * one-to-one interface over a single implementation is often ceremony. It earns
 * its place here for two reasons. It names the use case in the type system, so
 * the set of capabilities is readable from the package listing instead of
 * inferred from which classes happen to end in {@code Service}. And it is the
 * seam for decoration — metrics, retries, an idempotency guard — added around a
 * use case without editing it.
 */
public interface RegisterCustomer {

    Mono<Customer> register(RegisterCustomerCommand command);
}
