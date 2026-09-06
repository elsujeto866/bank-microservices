package com.bank.customer.application.service;

import com.bank.customer.application.command.PatchCustomerCommand;
import com.bank.customer.application.exception.CustomerNotFoundException;
import com.bank.customer.application.port.in.PatchCustomer;
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
 * Applies a partial update (HTTP PATCH).
 *
 * <p>Only the fields the caller actually supplied are touched. The command
 * models "absent" as {@link Optional#empty()}, which is what keeps a PATCH that
 * omits {@code address} from wiping the address — the single most common defect
 * in hand-rolled partial updates.
 *
 * <p>The order of operations is deliberate: profile first, then activation,
 * then the password. Activation is applied after the profile so that the
 * {@code CustomerProfileUpdated} event and the state change land in the order a
 * consumer will replay them.
 */
public class PatchCustomerService implements PatchCustomer {

    private final CustomerRepository customers;
    private final PasswordHasher passwordHasher;
    private final Clock clock;

    public PatchCustomerService(CustomerRepository customers, PasswordHasher passwordHasher, Clock clock) {
        this.customers = Objects.requireNonNull(customers, "customers must not be null");
        this.passwordHasher = Objects.requireNonNull(passwordHasher, "passwordHasher must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public Mono<Customer> patch(PatchCustomerCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        return customers.findById(command.customerId())
                .switchIfEmpty(Mono.error(() -> new CustomerNotFoundException(command.customerId())))
                .flatMap(customer -> applyTo(customer, command))
                .flatMap(customers::save);
    }

    private Mono<Customer> applyTo(Customer customer, PatchCustomerCommand command) {
        Instant now = clock.instant();

        if (command.touchesProfile()) {
            // Skipped when no profile field was supplied, so a PATCH that only
            // flips `active` does not emit a CustomerProfileUpdated event
            // announcing a change that never happened. Events must describe
            // reality, not the shape of the request that arrived.
            customer.updateProfile(command.applyTo(customer.personalData()), now);
        }

        command.active().ifPresent(shouldBeActive -> {
            if (shouldBeActive) {
                customer.activate(now);
            } else if (customer.isActive()) {
                customer.deactivate(now);
            }
        });

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
