package com.bank.customer.domain.model;

import com.bank.customer.domain.exception.InvalidValueException;
import java.util.regex.Pattern;

/**
 * A telephone number.
 *
 * <p>Stored as digits only. Separators, spaces and parentheses are formatting,
 * and formatting is a presentation concern — {@code "098 254 785"} and
 * {@code "(098)254-785"} are the same number, and a system that stores them as
 * two different strings cannot deduplicate, search or compare them.
 */
public record PhoneNumber(String value) {

    private static final int MIN_DIGITS = 7;
    private static final int MAX_DIGITS = 20;
    private static final Pattern ACCEPTED_INPUT = Pattern.compile("^[0-9+()\\-\\s]+$");
    private static final Pattern SEPARATORS = Pattern.compile("[()\\-\\s]");

    public PhoneNumber {
        if (value == null || value.isBlank()) {
            throw new InvalidValueException("phone", "phone must not be blank");
        }
        if (!ACCEPTED_INPUT.matcher(value).matches()) {
            throw new InvalidValueException(
                    "phone", "phone may only contain digits and the separators + ( ) - and spaces");
        }
        value = SEPARATORS.matcher(value.strip()).replaceAll("");
        long digits = value.chars().filter(Character::isDigit).count();
        if (digits < MIN_DIGITS || digits > MAX_DIGITS) {
            throw new InvalidValueException(
                    "phone", "phone must contain between %d and %d digits".formatted(MIN_DIGITS, MAX_DIGITS));
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
