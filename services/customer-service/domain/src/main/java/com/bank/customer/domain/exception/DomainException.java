package com.bank.customer.domain.exception;

/**
 * Base type for every failure the business rules can raise.
 *
 * <p>Two things matter here.
 *
 * <p>First, it carries a stable {@code errorCode}. That code — not the message —
 * is what the HTTP layer maps to an RFC 9457 problem type and what a client
 * branches on. Messages get reworded and translated; codes are a contract.
 *
 * <p>Second, it is a plain {@link RuntimeException} from {@code java.lang}. No
 * Spring, no HTTP status, no annotation. The domain does not know that HTTP
 * exists, and it must stay that way: the same rule has to be enforceable from a
 * REST call, a Kafka consumer or a batch job, and only one of those has a
 * status code.
 */
public abstract class DomainException extends RuntimeException {

    private final String errorCode;

    protected DomainException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
