package com.bank.customer.domain.model;

import com.bank.customer.domain.exception.InvalidValueException;
import java.util.regex.Pattern;

/**
 * National identification number.
 *
 * <p>This is the customer's natural key: uniqueness is enforced on it, not on
 * the surrogate {@link CustomerId}. Two records with the same identification
 * are the same human being with a duplicate row, and the database must refuse
 * that.
 *
 * <p>Normalised to upper case on construction so that {@code "a-123"} and
 * {@code "A-123"} cannot both exist. Uniqueness that is case-sensitive when the
 * real-world identifier is not is a uniqueness constraint that does not work.
 */
public record Identification(String value) {

    private static final int MIN_LENGTH = 5;
    private static final int MAX_LENGTH = 20;
    private static final Pattern ALLOWED = Pattern.compile("^[A-Za-z0-9-]+$");

    public Identification {
        if (value == null || value.isBlank()) {
            throw new InvalidValueException("identification", "identification must not be blank");
        }
        value = value.strip().toUpperCase();
        if (value.length() < MIN_LENGTH || value.length() > MAX_LENGTH) {
            throw new InvalidValueException(
                    "identification",
                    "identification must be between %d and %d characters".formatted(MIN_LENGTH, MAX_LENGTH));
        }
        if (!ALLOWED.matcher(value).matches()) {
            throw new InvalidValueException(
                    "identification", "identification may only contain letters, digits and hyphens");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
