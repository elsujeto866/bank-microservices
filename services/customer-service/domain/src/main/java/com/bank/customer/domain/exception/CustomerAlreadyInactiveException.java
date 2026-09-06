package com.bank.customer.domain.exception;

import com.bank.customer.domain.model.CustomerId;

/**
 * Raised when deactivating a customer that is already inactive.
 *
 * <p>Deliberately an error rather than a silent no-op. "Deactivate the
 * already-deactivated" almost always means the caller is working from stale
 * state, and swallowing it hides the real problem — a lost update, a duplicated
 * request, a race between two operators.
 */
public class CustomerAlreadyInactiveException extends DomainException {

    private static final String ERROR_CODE = "customer-already-inactive";

    public CustomerAlreadyInactiveException(CustomerId customerId) {
        super(ERROR_CODE, "Customer %s is already inactive".formatted(customerId));
    }
}
