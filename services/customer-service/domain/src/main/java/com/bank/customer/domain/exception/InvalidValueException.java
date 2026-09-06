package com.bank.customer.domain.exception;

/**
 * Raised when a value object is handed something it cannot legally hold.
 *
 * <p>Carries the offending field so the boundary can report it per-field
 * instead of collapsing every validation failure into one opaque message.
 */
public class InvalidValueException extends DomainException {

    private static final String ERROR_CODE = "invalid-value";

    private final String field;

    public InvalidValueException(String field, String message) {
        super(ERROR_CODE, message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
