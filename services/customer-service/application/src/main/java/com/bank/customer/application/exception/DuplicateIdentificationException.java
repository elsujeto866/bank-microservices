package com.bank.customer.application.exception;

import com.bank.customer.domain.model.Identification;

/**
 * Another customer already holds this identification. Maps to HTTP 409.
 *
 * <p>Raised from two places, and both are needed:
 *
 * <ol>
 *   <li>The use case checks first, so the common case gets a clear message
 *       without depending on a database error string.
 *   <li>The persistence adapter translates the unique-constraint violation.
 *       <strong>This one is the actual guard.</strong> Between the check and
 *       the insert, another request can slip in — a classic time-of-check to
 *       time-of-use race. Only the database can settle it, because only the
 *       database sees both writes.
 * </ol>
 *
 * <p>A pre-check without the constraint is a race. A constraint without the
 * pre-check is a worse error message. Ship both.
 */
public class DuplicateIdentificationException extends ApplicationException {

    private static final String ERROR_CODE = "duplicate-identification";

    public DuplicateIdentificationException(Identification identification) {
        super(ERROR_CODE, "A customer already exists with identification %s".formatted(identification));
    }
}
