package com.bank.customer.infrastructure.persistence;

import com.bank.customer.application.port.out.CustomerRepository;
import com.bank.customer.application.query.CustomerFilter;
import com.bank.customer.application.shared.Page;
import com.bank.customer.application.shared.PageRequest;
import com.bank.customer.domain.model.Customer;
import com.bank.customer.domain.model.CustomerId;
import com.bank.customer.domain.model.Identification;
import com.bank.customer.infrastructure.observability.CorrelationIdContext;
import java.util.List;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

/**
 * The reactive half of the persistence adapter, and the concrete answer to
 * ADR-0003.
 *
 * <p>Every method follows the same two-line shape:
 *
 * <pre>{@code
 * Mono.fromCallable(() -> gateway.doSomethingBlocking())
 *     .subscribeOn(jdbcScheduler);
 * }</pre>
 *
 * <p>Two operators, and each one is doing something specific.
 *
 * <p>{@code Mono.fromCallable} defers the work. Without it the JDBC call would
 * run immediately, on whatever thread built the pipeline, before anyone
 * subscribed — the classic "my reactive code blocks and I do not know why".
 *
 * <p>{@code subscribeOn} moves the subscription, and therefore the blocking
 * call, onto {@code jdbcScheduler}. The event-loop thread is released the
 * instant the pipeline is assembled and goes back to serving other connections.
 * A worker thread blocks on the database socket instead, which is precisely
 * what worker threads are for.
 *
 * <p><strong>This does not make JPA non-blocking.</strong> Nothing can. It
 * relocates the block off the four or eight threads that serve every request in
 * the process, onto a pool sized to match the connection pool. The event loop
 * stays responsive under load; the database path's throughput ceiling is still
 * the size of that pool, exactly as it would be with Spring MVC.
 *
 * <p>Note {@code subscribeOn} rather than {@code publishOn}: {@code subscribeOn}
 * decides where the <em>source</em> runs, no matter where it sits in the chain,
 * which is what we need. {@code publishOn} only affects operators downstream of
 * it and would leave the blocking source exactly where it was.
 */
public class CustomerRepositoryAdapter implements CustomerRepository {

    private final CustomerPersistenceGateway gateway;
    private final Scheduler jdbcScheduler;

    public CustomerRepositoryAdapter(CustomerPersistenceGateway gateway, Scheduler jdbcScheduler) {
        this.gateway = gateway;
        this.jdbcScheduler = jdbcScheduler;
    }

    @Override
    public Mono<Customer> save(Customer customer) {
        return CorrelationIdContext.current()
                .flatMap(correlationId -> Mono.fromCallable(() -> gateway.save(customer, correlationId))
                        .subscribeOn(jdbcScheduler));
    }

    @Override
    public Mono<Customer> findById(CustomerId id) {
        // Mono.justOrEmpty on an Optional inside fromCallable would eagerly
        // evaluate; flatMap keeps the whole thing deferred and on the scheduler.
        return Mono.fromCallable(() -> gateway.findById(id))
                .subscribeOn(jdbcScheduler)
                .flatMap(Mono::justOrEmpty);
    }

    @Override
    public Mono<Customer> findByIdentification(Identification identification) {
        return Mono.fromCallable(() -> gateway.findByIdentification(identification.value()))
                .subscribeOn(jdbcScheduler)
                .flatMap(Mono::justOrEmpty);
    }

    @Override
    public Mono<Boolean> existsByIdentification(Identification identification) {
        return Mono.fromCallable(() -> gateway.existsByIdentification(identification.value()))
                .subscribeOn(jdbcScheduler);
    }

    @Override
    public Mono<Page<Customer>> findAll(CustomerFilter filter, PageRequest pageRequest) {
        String identification =
                filter.identification().map(Identification::value).orElse(null);
        Boolean active = filter.active().orElse(null);

        return Mono.fromCallable(() -> gateway.search(identification, active, pageRequest.page(), pageRequest.size()))
                .subscribeOn(jdbcScheduler)
                .map(springPage -> {
                    List<Customer> content =
                            springPage.getContent().stream().map(gateway::toDomain).toList();
                    // Spring's Page is deliberately not returned to the caller.
                    // Serialising a framework class publishes its internals as
                    // your contract, and Spring has changed that shape before.
                    return new Page<>(
                            content, pageRequest.page(), pageRequest.size(), springPage.getTotalElements());
                });
    }
}
