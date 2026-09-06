package com.bank.customer.application.query;

import com.bank.customer.domain.model.Identification;
import java.util.Objects;
import java.util.Optional;

/**
 * Optional criteria for listing customers.
 *
 * <p>Both fields empty means "no filter", which is why {@link #none()} exists
 * as a named constant rather than callers passing {@code null} and every
 * implementation having to guess what that means.
 */
public record CustomerFilter(Optional<Identification> identification, Optional<Boolean> active) {

    private static final CustomerFilter NONE = new CustomerFilter(Optional.empty(), Optional.empty());

    public CustomerFilter {
        Objects.requireNonNull(identification, "identification must not be null; use Optional.empty()");
        Objects.requireNonNull(active, "active must not be null; use Optional.empty()");
    }

    public static CustomerFilter none() {
        return NONE;
    }

    public static CustomerFilter byIdentification(Identification identification) {
        return new CustomerFilter(Optional.of(identification), Optional.empty());
    }

    public boolean isEmpty() {
        return identification.isEmpty() && active.isEmpty();
    }
}
