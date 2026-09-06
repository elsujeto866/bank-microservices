package com.bank.customer.domain.model;

import com.bank.customer.domain.exception.InvalidValueException;
import java.util.Objects;
import java.util.UUID;

/**
 * Identity of a customer.
 *
 * <p>A wrapper around a UUID rather than a bare {@code UUID}, because a bare
 * UUID is assignable to any other bare UUID. {@code transfer(fromId, toId)}
 * with the arguments swapped compiles perfectly and fails in production;
 * {@code transfer(CustomerId, AccountId)} does not compile at all.
 *
 * <p>The type system is the cheapest test suite available. Use it.
 */
public record CustomerId(UUID value) {

    public CustomerId {
        Objects.requireNonNull(value, "customerId must not be null");
    }

    /**
     * Parses an external representation, typically a path variable.
     *
     * @throws InvalidValueException if the text is not a UUID — a malformed id
     *         is a validation failure at the boundary, not an
     *         {@code IllegalArgumentException} escaping from deep inside.
     */
    public static CustomerId of(String raw) {
        try {
            return new CustomerId(UUID.fromString(raw));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidValueException("customerId", "customerId must be a valid UUID");
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
