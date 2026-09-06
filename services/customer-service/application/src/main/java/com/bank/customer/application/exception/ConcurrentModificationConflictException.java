package com.bank.customer.application.exception;

import com.bank.customer.domain.model.CustomerId;

/**
 * The customer was modified by someone else between our read and our write.
 * Maps to HTTP 409.
 *
 * <p>Raised by the persistence adapter when optimistic locking detects a stale
 * version.
 *
 * <p>Why optimistic locking rather than a transaction held open across the
 * read-modify-write: holding a row lock for the duration of a use case
 * serialises every concurrent request against that customer and, on a reactive
 * stack, pins a scarce worker thread while it waits. Optimistic locking takes
 * no lock at all — it lets both writers proceed and rejects the second one at
 * commit. Conflicts on a single customer record are rare; paying a lock on
 * every write to make the rare case cheaper is the wrong trade.
 */
public class ConcurrentModificationConflictException extends ApplicationException {

    private static final String ERROR_CODE = "concurrent-modification";

    public ConcurrentModificationConflictException(CustomerId customerId) {
        super(ERROR_CODE, "Customer %s was modified concurrently; retry the operation".formatted(customerId));
    }
}
