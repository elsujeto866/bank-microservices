package com.bank.customer.application.service;

import com.bank.customer.application.exception.CustomerNotFoundException;
import com.bank.customer.application.port.in.DeactivateCustomer;
import com.bank.customer.application.port.out.CustomerRepository;
import com.bank.customer.domain.model.CustomerId;
import java.time.Clock;
import java.util.Objects;
import reactor.core.publisher.Mono;

/**
 * Deactivates a customer.
 *
 * <p>Load, tell the aggregate what happened, save. The service never asks
 * whether the customer is already inactive — {@link
 * com.bank.customer.domain.model.Customer#deactivate} owns that rule, and
 * duplicating the check here would create a second place to keep in sync and a
 * second place to get it wrong.
 *
 * <p>The read-modify-write is not wrapped in a long transaction on purpose.
 * Holding a lock across the whole use case serialises every request touching
 * this customer and, on a reactive stack, pins a scarce worker thread while it
 * waits. Optimistic locking in the adapter rejects the losing write at commit
 * instead, and the caller retries. See {@code
 * ConcurrentModificationConflictException}.
 */
public class DeactivateCustomerService implements DeactivateCustomer {

    private final CustomerRepository customers;
    private final Clock clock;

    public DeactivateCustomerService(CustomerRepository customers, Clock clock) {
        this.customers = Objects.requireNonNull(customers, "customers must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public Mono<Void> deactivate(CustomerId customerId) {
        Objects.requireNonNull(customerId, "customerId must not be null");

        return customers.findById(customerId)
                .switchIfEmpty(Mono.error(() -> new CustomerNotFoundException(customerId)))
                .flatMap(customer -> {
                    customer.deactivate(clock.instant());
                    return customers.save(customer);
                })
                .then();
    }
}
