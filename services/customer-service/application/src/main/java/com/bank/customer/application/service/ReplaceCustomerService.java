package com.bank.customer.application.service;

import com.bank.customer.application.command.ReplaceCustomerCommand;
import com.bank.customer.application.exception.CustomerNotFoundException;
import com.bank.customer.application.exception.ImmutableIdentificationException;
import com.bank.customer.application.port.in.ReplaceCustomer;
import com.bank.customer.application.port.out.CustomerRepository;
import com.bank.customer.application.port.out.PasswordHasher;
import com.bank.customer.domain.model.Customer;
import com.bank.customer.domain.model.HashedPassword;
import com.bank.customer.domain.model.PlainPassword;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import reactor.core.publisher.Mono;

/**
 * Replaces a customer's profile wholesale (HTTP PUT).
 *
 * <p>The request carries {@code identification} because PUT sends a complete
 * representation, but it may only ever carry the value the customer already
 * has. A different value is rejected with {@link
 * ImmutableIdentificationException} rather than quietly discarded — the domain
 * makes the field {@code final}, so accepting the request and answering 200
 * would tell the caller a change succeeded when nothing happened.
 *
 * <p>A pleasant consequence: because identification cannot move, this use case
 * needs no uniqueness check and no lookup for it. The constraint that guards
 * the natural key is only ever exercised on registration.
 */
public class ReplaceCustomerService implements ReplaceCustomer {

    private final CustomerRepository customers;
    private final PasswordHasher passwordHasher;
    private final Clock clock;

    public ReplaceCustomerService(CustomerRepository customers, PasswordHasher passwordHasher, Clock clock) {
        this.customers = Objects.requireNonNull(customers, "customers must not be null");
        this.passwordHasher = Objects.requireNonNull(passwordHasher, "passwordHasher must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public Mono<Customer> replace(ReplaceCustomerCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        return customers.findById(command.customerId())
                .switchIfEmpty(Mono.error(() -> new CustomerNotFoundException(command.customerId())))
                .flatMap(customer -> rejectIdentificationChange(customer, command))
                .flatMap(customer -> applyTo(customer, command))
                .flatMap(customers::save);
    }

    private Mono<Customer> rejectIdentificationChange(Customer customer, ReplaceCustomerCommand command) {
        if (!customer.identification().equals(command.profile().identification())) {
            return Mono.error(new ImmutableIdentificationException(
                    customer.id(), command.profile().identification()));
        }
        return Mono.just(customer);
    }

    private Mono<Customer> applyTo(Customer customer, ReplaceCustomerCommand command) {
        Instant now = clock.instant();
        customer.updateProfile(command.profile(), now);

        if (command.active()) {
            customer.activate(now);
        } else if (customer.isActive()) {
            // Guarded, because deactivate() rejects a second deactivation by
            // design. A PUT that resends `active: false` on an already inactive
            // customer is not the stale-state situation that rule protects
            // against — it is a full replacement that happens to agree with
            // reality, and it must succeed.
            customer.deactivate(now);
        }

        return applyPasswordChange(customer, command.password(), now);
    }

    private Mono<Customer> applyPasswordChange(Customer customer, Optional<PlainPassword> maybePassword, Instant now) {
        return maybePassword
                .map(password -> passwordHasher.hash(password).map((HashedPassword hashed) -> {
                    customer.changePassword(hashed, now);
                    return customer;
                }))
                .orElseGet(() -> Mono.just(customer));
    }
}
