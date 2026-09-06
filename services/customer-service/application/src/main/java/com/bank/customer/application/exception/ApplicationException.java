package com.bank.customer.application.exception;

/**
 * Base type for failures that belong to the use-case layer rather than to the
 * business rules.
 *
 * <p>The split matters. "A password must be at least four characters" is a
 * domain rule: true regardless of whether anything is stored anywhere.
 * "No customer exists with this id" is not a rule at all — it is a fact about
 * the current contents of a repository, and it can only be discovered by asking
 * one. Keeping the two apart is what lets the domain stay free of persistence.
 *
 * <p>Like {@code DomainException}, it carries a stable {@code errorCode} and
 * knows nothing about HTTP.
 */
public abstract class ApplicationException extends RuntimeException {

    private final String errorCode;

    protected ApplicationException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
