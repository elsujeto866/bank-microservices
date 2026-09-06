package com.bank.customer.domain.model;

import com.bank.customer.domain.exception.InvalidValueException;

/**
 * The stored form of a password.
 *
 * <p>A distinct type from {@link PlainPassword} on purpose. Once the two are
 * different types, {@code save(hashedPassword)} cannot accidentally be handed a
 * plaintext one — the compiler refuses. That single distinction removes an
 * entire class of "we forgot to hash it" defect.
 *
 * <p>The domain knows a hash exists. It does not know which algorithm produced
 * it: hashing is I/O-adjacent, algorithm choices rotate, and that belongs
 * behind a port in the infrastructure layer.
 */
public record HashedPassword(String value) {

    public HashedPassword {
        if (value == null || value.isBlank()) {
            throw new InvalidValueException("password", "hashed password must not be blank");
        }
    }

    @Override
    public String toString() {
        return "HashedPassword[****]";
    }
}
