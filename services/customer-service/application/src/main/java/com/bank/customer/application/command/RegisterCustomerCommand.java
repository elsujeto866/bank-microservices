package com.bank.customer.application.command;

import com.bank.customer.domain.model.PersonalData;
import com.bank.customer.domain.model.PlainPassword;
import java.util.Objects;

/**
 * Input to registering a customer.
 *
 * <p>A distinct type from the generated {@code CreateCustomerRequest} DTO, and
 * that separation is the point of contract-first rather than a side effect of
 * it. The DTO is shaped by the wire format and changes when the API changes;
 * the command is shaped by the use case and changes when the business changes.
 * Those are different clocks.
 *
 * <p>Note the fields are already domain value objects, not raw strings. The
 * controller parses and validates on the way in, so by the time a use case
 * receives a command every value in it is known to be legal. A use case that
 * accepts {@code String name} has to re-validate, and eventually one of them
 * forgets.
 */
public record RegisterCustomerCommand(PersonalData profile, PlainPassword password, boolean active) {

    public RegisterCustomerCommand {
        Objects.requireNonNull(profile, "profile must not be null");
        Objects.requireNonNull(password, "password must not be null");
    }
}
