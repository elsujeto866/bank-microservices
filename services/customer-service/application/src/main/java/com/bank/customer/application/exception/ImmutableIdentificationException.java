package com.bank.customer.application.exception;

import com.bank.customer.domain.model.CustomerId;
import com.bank.customer.domain.model.Identification;

/**
 * A request tried to change a customer's identification. Maps to HTTP 422.
 *
 * <p>Identification is the natural key: it is what makes two records the same
 * human being, it carries the uniqueness constraint, and accounts in the other
 * service are reconciled against it. Changing it is an identity change, not a
 * profile edit — a different operation with different authorisation, a
 * different audit trail and different downstream consequences.
 *
 * <p>The domain enforces this structurally: {@code Person.identification} is
 * {@code final} and {@code changeProfile} has no parameter for it. This
 * exception exists so the attempt is <strong>reported</strong> rather than
 * silently discarded.
 *
 * <p>That distinction is the whole reason this class exists. A PUT that accepts
 * a new identification, ignores it, and answers 200 tells the caller the change
 * succeeded when nothing happened. Silent no-ops are the hardest class of bug
 * to find, because there is nothing to find — no error, no log line, no failed
 * request. Just a system that quietly disagrees with its users about what is
 * true.
 */
public class ImmutableIdentificationException extends ApplicationException {

    private static final String ERROR_CODE = "identification-is-immutable";

    public ImmutableIdentificationException(CustomerId customerId, Identification attempted) {
        super(
                ERROR_CODE,
                "Customer %s cannot change its identification to %s; identification is immutable"
                        .formatted(customerId, attempted));
    }
}
