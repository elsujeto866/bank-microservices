package com.bank.customer.application.command;

import com.bank.customer.domain.model.CustomerId;
import com.bank.customer.domain.model.PersonalData;
import com.bank.customer.domain.model.PlainPassword;
import java.util.Objects;
import java.util.Optional;

/**
 * Input to a full replacement (HTTP PUT).
 *
 * <p>{@code password} is optional because replacing a profile should not force
 * the caller to resend a credential it may not hold — an administrator editing
 * an address does not know the customer's password, and requiring one would
 * mean either inventing a placeholder or refusing the edit.
 *
 * <p>{@link Optional} appears here as a parameter, which is exactly what it was
 * designed for: an explicit "there may be nothing". The usual advice against
 * {@code Optional} concerns fields and serialisation, and neither applies.
 */
public record ReplaceCustomerCommand(
        CustomerId customerId, PersonalData profile, Optional<PlainPassword> password, boolean active) {

    public ReplaceCustomerCommand {
        Objects.requireNonNull(customerId, "customerId must not be null");
        Objects.requireNonNull(profile, "profile must not be null");
        Objects.requireNonNull(password, "password must not be null; use Optional.empty()");
    }
}
