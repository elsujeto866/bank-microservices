package com.bank.customer.domain.model;

import com.bank.customer.domain.exception.InvalidValueException;

/** A postal address, kept as a single free-text line as the exercise specifies. */
public record Address(String value) {

    private static final int MIN_LENGTH = 3;
    private static final int MAX_LENGTH = 200;

    public Address {
        if (value == null || value.isBlank()) {
            throw new InvalidValueException("address", "address must not be blank");
        }
        value = value.strip();
        if (value.length() < MIN_LENGTH || value.length() > MAX_LENGTH) {
            throw new InvalidValueException(
                    "address", "address must be between %d and %d characters".formatted(MIN_LENGTH, MAX_LENGTH));
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
