package com.bank.customer.application.shared;

/**
 * A request for one page of results.
 *
 * <p>Our own type, not Spring's {@code Pageable}. The application layer is not
 * allowed to see Spring — the build enforces it — but the deeper reason is that
 * pagination is a use-case concept. If it were Spring's type, replacing the web
 * framework would mean touching every use case, and the use cases have nothing
 * to do with the web.
 */
public record PageRequest(int page, int size) {

    /**
     * Hard ceiling on page size.
     *
     * <p>Enforced here as well as in the OpenAPI schema, on purpose. The
     * contract stops a careless HTTP caller; this stops every other entry point
     * — a Kafka consumer, a batch job, a future gRPC facade. A limit that only
     * exists at one door is not a limit.
     */
    public static final int MAX_SIZE = 100;

    public static final PageRequest DEFAULT = new PageRequest(0, 20);

    public PageRequest {
        if (page < 0) {
            throw new IllegalArgumentException("page must not be negative");
        }
        if (size < 1) {
            throw new IllegalArgumentException("size must be at least 1");
        }
        if (size > MAX_SIZE) {
            throw new IllegalArgumentException("size must not exceed " + MAX_SIZE);
        }
    }

    public long offset() {
        return (long) page * size;
    }
}
