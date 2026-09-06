package com.bank.customer.application.service;

import com.bank.customer.application.exception.CustomerNotFoundException;
import com.bank.customer.application.port.in.GetCustomer;
import com.bank.customer.application.port.out.CustomerRepository;
import com.bank.customer.domain.model.Customer;
import com.bank.customer.domain.model.CustomerId;
import java.util.Objects;
import reactor.core.publisher.Mono;

/**
 * Fetches one customer.
 *
 * <p>Turns "not found" from an empty {@link Mono} into an explicit failure. The
 * repository is right to return empty — absence is not an error to a
 * repository. The use case is right to fail — its caller has to answer 404, and
 * an empty {@code Mono} silently becomes a 200 with no body the moment somebody
 * forgets a {@code switchIfEmpty}.
 */
public class GetCustomerService implements GetCustomer {

    private final CustomerRepository customers;

    public GetCustomerService(CustomerRepository customers) {
        this.customers = Objects.requireNonNull(customers, "customers must not be null");
    }

    @Override
    public Mono<Customer> byId(CustomerId customerId) {
        Objects.requireNonNull(customerId, "customerId must not be null");

        return customers.findById(customerId)
                // Mono.error(Supplier) — the exception is built only if the Mono
                // is actually empty. The eager overload would allocate it, and
                // capture a stack trace, on every successful lookup.
                .switchIfEmpty(Mono.error(() -> new CustomerNotFoundException(customerId)));
    }
}
