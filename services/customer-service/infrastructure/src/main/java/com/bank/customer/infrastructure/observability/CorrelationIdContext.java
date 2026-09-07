package com.bank.customer.infrastructure.observability;

import java.util.UUID;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * Carries a correlation id through a reactive pipeline.
 *
 * <p><strong>Why not a ThreadLocal?</strong> Because in WebFlux there is no
 * "current thread" for a request. A single request hops between the event loop,
 * the JDBC scheduler and back, and a {@code ThreadLocal} set on one of those
 * threads is invisible on the next one — and worse, is still visible to the
 * <em>next request</em> that lands on the original thread. MDC-based logging
 * copied from a servlet application fails in exactly this way: it works in
 * development and attaches the wrong id under load.
 *
 * <p>Reactor's {@link Context} travels with the subscription instead of with
 * the thread, so it stays correct across every hop.
 *
 * <p>The id is written once, by the web filter, and read wherever a log line or
 * an outbound event needs it. It is what turns a failure spanning two services
 * and a broker from three unrelated log files into one query.
 */
public final class CorrelationIdContext {

    public static final String HEADER = "X-Correlation-Id";
    public static final String CONTEXT_KEY = "correlationId";

    /** Used when nothing upstream supplied one — a scheduled job, or a test. */
    public static final String ABSENT = "none";

    private CorrelationIdContext() {}

    /** Puts the id into the subscription context. */
    public static Context with(String correlationId) {
        return Context.of(CONTEXT_KEY, correlationId);
    }

    /**
     * Reads the id, falling back to {@link #ABSENT}.
     *
     * <p>Never empty and never failing: a missing correlation id must not turn
     * a working request into an error. Observability is there to explain a
     * failure, not to cause one.
     */
    public static Mono<String> current() {
        return Mono.deferContextual(contextView ->
                Mono.just(contextView.getOrDefault(CONTEXT_KEY, ABSENT)));
    }

    public static String newId() {
        return UUID.randomUUID().toString();
    }
}
