package com.bank.customer.domain.model;

/**
 * A closed set of values.
 *
 * <p>An enum rather than a {@code String}: an invalid gender cannot be
 * constructed, so no code downstream needs to check for one.
 */
public enum Gender {
    MALE,
    FEMALE,
    OTHER,
    UNSPECIFIED
}
