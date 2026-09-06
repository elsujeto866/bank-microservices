package com.bank.customer.domain.model;

import com.bank.customer.domain.exception.InvalidValueException;

/**
 * A password as the customer typed it, before hashing.
 *
 * <p>Exists only to carry the value from the boundary to the hasher and to
 * enforce the length rules in one place.
 *
 * <p>Two details are not decoration:
 *
 * <ul>
 *   <li>{@link #toString()} is masked. A record's generated {@code toString}
 *       prints every component, so the default would put the password into any
 *       log line, exception message or debugger view that touches it. Most
 *       credential leaks are exactly this and nothing more exotic.
 *   <li>The 72-byte ceiling is not arbitrary. BCrypt silently truncates input
 *       beyond 72 bytes, so a longer password is accepted and then quietly not
 *       fully used. Reject it instead of pretending it was honoured.
 * </ul>
 */
public record PlainPassword(String value) {

    private static final int MIN_LENGTH = 4;
    private static final int MAX_BYTES = 72;

    public PlainPassword {
        if (value == null || value.isEmpty()) {
            throw new InvalidValueException("password", "password must not be empty");
        }
        if (value.length() < MIN_LENGTH) {
            throw new InvalidValueException(
                    "password", "password must be at least %d characters".formatted(MIN_LENGTH));
        }
        if (value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new InvalidValueException(
                    "password", "password must not exceed %d bytes".formatted(MAX_BYTES));
        }
    }

    @Override
    public String toString() {
        return "PlainPassword[****]";
    }
}
