package com.bank.customer.domain.model;

import java.util.Objects;

/**
 * The five attributes the exercise assigns to a person, grouped as one value.
 *
 * <p>A parameter object, so that registering or updating a customer is one
 * argument instead of five, and so that an event can carry a complete profile
 * without restating the field list.
 *
 * <p>{@link Person} still declares the five fields individually — this is the
 * shape they travel in, not where they live.
 */
public record PersonalData(
        PersonName name, Gender gender, Identification identification, Address address, PhoneNumber phone) {

    public PersonalData {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(gender, "gender must not be null");
        Objects.requireNonNull(identification, "identification must not be null");
        Objects.requireNonNull(address, "address must not be null");
        Objects.requireNonNull(phone, "phone must not be null");
    }
}
