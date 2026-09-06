package com.bank.customer.application.command;

import com.bank.customer.domain.model.Address;
import com.bank.customer.domain.model.CustomerId;
import com.bank.customer.domain.model.Gender;
import com.bank.customer.domain.model.PersonName;
import com.bank.customer.domain.model.PersonalData;
import com.bank.customer.domain.model.PhoneNumber;
import com.bank.customer.domain.model.PlainPassword;
import java.util.Objects;
import java.util.Optional;

/**
 * Input to a partial update (HTTP PATCH).
 *
 * <p>Every attribute is {@link Optional}: empty means "leave this alone", not
 * "set it to null". Collapsing those two into a single nullable field is the
 * classic PATCH bug — the caller sends nothing, the code reads {@code null},
 * and the address is wiped.
 *
 * <p>The command does not decide what the new state is. It carries the
 * caller's intent; {@link #applyTo(PersonalData)} folds that intent onto the
 * state the aggregate actually holds, which the use case has just loaded.
 */
public record PatchCustomerCommand(
        CustomerId customerId,
        Optional<PersonName> name,
        Optional<Gender> gender,
        Optional<Address> address,
        Optional<PhoneNumber> phone,
        Optional<PlainPassword> password,
        Optional<Boolean> active) {

    public PatchCustomerCommand {
        Objects.requireNonNull(customerId, "customerId must not be null");
        Objects.requireNonNull(name, "name must not be null; use Optional.empty()");
        Objects.requireNonNull(gender, "gender must not be null; use Optional.empty()");
        Objects.requireNonNull(address, "address must not be null; use Optional.empty()");
        Objects.requireNonNull(phone, "phone must not be null; use Optional.empty()");
        Objects.requireNonNull(password, "password must not be null; use Optional.empty()");
        Objects.requireNonNull(active, "active must not be null; use Optional.empty()");
    }

    /** True when the caller asked for no profile change at all. */
    public boolean touchesProfile() {
        return name.isPresent() || gender.isPresent() || address.isPresent() || phone.isPresent();
    }

    /**
     * Produces the new profile by overlaying the supplied fields onto the
     * current one.
     *
     * <p>{@code identification} is never part of this: it is the natural key,
     * and changing it is an identity change rather than a profile edit.
     */
    public PersonalData applyTo(PersonalData current) {
        return new PersonalData(
                name.orElse(current.name()),
                gender.orElse(current.gender()),
                current.identification(),
                address.orElse(current.address()),
                phone.orElse(current.phone()));
    }
}
