package com.bank.customer.domain.model;

import com.bank.customer.domain.exception.InvalidValueException;

/**
 * A person's full name.
 *
 * <p>Validated once, at construction. After that, every method that accepts a
 * {@code PersonName} can stop asking whether it is blank or absurdly long —
 * an instance cannot exist in an invalid state.
 *
 * <p>That is the whole argument for value objects: validation happens at the
 * boundary of the type, not at the top of every method that touches it. The
 * alternative is the same three null checks copy-pasted into fifteen places,
 * one of which is missing them.
 */
public record PersonName(String value) {

    private static final int MIN_LENGTH = 2;
    private static final int MAX_LENGTH = 120;

    public PersonName {
        if (value == null || value.isBlank()) {
            throw new InvalidValueException("name", "name must not be blank");
        }
        value = value.strip();
        if (value.length() < MIN_LENGTH || value.length() > MAX_LENGTH) {
            throw new InvalidValueException(
                    "name", "name must be between %d and %d characters".formatted(MIN_LENGTH, MAX_LENGTH));
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
