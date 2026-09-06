package com.bank.customer.application.service;

import com.bank.customer.application.command.RegisterCustomerCommand;
import com.bank.customer.application.exception.DuplicateIdentificationException;
import com.bank.customer.application.port.in.RegisterCustomer;
import com.bank.customer.application.port.out.CustomerRepository;
import com.bank.customer.application.port.out.IdentifierGenerator;
import com.bank.customer.application.port.out.PasswordHasher;
import com.bank.customer.domain.model.Customer;
import java.time.Clock;
import java.util.Objects;
import reactor.core.publisher.Mono;

/**
 * Registers a new customer.
 *
 * <p>A use case is a thin orchestrator. It gathers what the domain needs, calls
 * one domain operation, and persists the result. Every rule about what a
 * customer may be lives in {@link Customer}; nothing about it lives here. When
 * a service starts growing {@code if} statements about business state, the rule
 * has escaped the model and the model has become a data holder.
 *
 * <p>Constructor injection with {@code final} fields, and not a single Spring
 * annotation — no {@code @Service}, no {@code @Autowired}. This class does not
 * know a container exists. The infrastructure layer declares it as a bean in a
 * {@code @Configuration}, which is the only place that knows how the wiring is
 * done. Swap Spring for anything else and this file is untouched.
 */
public class RegisterCustomerService implements RegisterCustomer {

    private final CustomerRepository customers;
    private final PasswordHasher passwordHasher;
    private final IdentifierGenerator identifiers;
    private final Clock clock;

    public RegisterCustomerService(
            CustomerRepository customers, PasswordHasher passwordHasher, IdentifierGenerator identifiers, Clock clock) {
        this.customers = Objects.requireNonNull(customers, "customers must not be null");
        this.passwordHasher = Objects.requireNonNull(passwordHasher, "passwordHasher must not be null");
        this.identifiers = Objects.requireNonNull(identifiers, "identifiers must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public Mono<Customer> register(RegisterCustomerCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        return customers.existsByIdentification(command.profile().identification())
                .flatMap(alreadyExists -> alreadyExists
                        ? Mono.<Customer>error(
                                new DuplicateIdentificationException(command.profile().identification()))
                        : hashAndSave(command));
    }

    private Mono<Customer> hashAndSave(RegisterCustomerCommand command) {
        return passwordHasher
                .hash(command.password())
                .map(hashed -> Customer.register(
                        identifiers.newCustomerId(),
                        command.profile(),
                        hashed,
                        command.active(),
                        clock.instant()))
                .flatMap(customers::save);
    }
}
