package com.bank.customer.infrastructure.persistence;

import com.bank.customer.application.exception.ConcurrentModificationConflictException;
import com.bank.customer.application.exception.DuplicateIdentificationException;
import com.bank.customer.domain.event.CustomerEvent;
import com.bank.customer.domain.model.Customer;
import com.bank.customer.domain.model.CustomerId;
import com.bank.customer.infrastructure.persistence.entity.CustomerEntity;
import com.bank.customer.infrastructure.persistence.mapper.CustomerEntityMapper;
import com.bank.customer.infrastructure.persistence.mapper.OutboxEventFactory;
import com.bank.customer.infrastructure.persistence.repository.CustomerJpaRepository;
import com.bank.customer.infrastructure.persistence.repository.OutboxJpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The blocking half of the persistence adapter.
 *
 * <p>Everything here runs on a plain thread and blocks on JDBC. That is the
 * point: {@code @Transactional} keeps its state in a {@code ThreadLocal}, so a
 * transactional unit of work must begin and end on <strong>one</strong> thread.
 * Split it across reactive operators and the transaction silently does not
 * apply — no error, just a commit that was never part of the transaction you
 * thought it was.
 *
 * <p>{@link CustomerRepositoryAdapter} is the reactive half. It calls these
 * methods on a dedicated scheduler so no Netty event-loop thread ever waits on
 * a database socket (ADR-0003). The two-class split is what makes the boundary
 * between blocking and non-blocking a visible fact rather than a convention
 * somebody has to remember.
 *
 * <p>A second reason the split matters: {@code @Transactional} works through a
 * Spring proxy, which only intercepts calls arriving from <em>outside</em> the
 * bean. A self-call inside one class bypasses the proxy entirely and silently
 * runs without a transaction. Putting the transactional methods on a separate
 * bean makes that mistake impossible to make by accident.
 */
@Component
public class CustomerPersistenceGateway {

    private static final Logger log = LoggerFactory.getLogger(CustomerPersistenceGateway.class);

    private final CustomerJpaRepository customers;
    private final OutboxJpaRepository outbox;
    private final CustomerEntityMapper mapper;
    private final OutboxEventFactory outboxEvents;

    public CustomerPersistenceGateway(
            CustomerJpaRepository customers,
            OutboxJpaRepository outbox,
            CustomerEntityMapper mapper,
            OutboxEventFactory outboxEvents) {
        this.customers = customers;
        this.outbox = outbox;
        this.mapper = mapper;
        this.outboxEvents = outboxEvents;
    }

    /**
     * Persists the aggregate and its pending events in one transaction.
     *
     * <p>This single method is the transactional outbox. The customer row and
     * the event rows are committed together or not at all, which is what
     * removes the window where a crash leaves a state change nobody downstream
     * will ever hear about.
     *
     * <p>{@code customer.pullEvents()} is called exactly once, here, and it
     * drains — so an aggregate that somehow reached this method twice cannot
     * enqueue its events twice.
     */
    @Transactional
    public Customer save(Customer customer, String correlationId) {
        CustomerEntity entity = customers
                .findById(customer.id().value())
                .map(existing -> {
                    mapper.applyTo(existing, customer);
                    return existing;
                })
                .orElseGet(() -> mapper.toEntity(customer));

        List<CustomerEvent> events = customer.pullEvents();

        try {
            CustomerEntity saved = customers.saveAndFlush(entity);
            events.forEach(event -> outbox.save(outboxEvents.from(event, correlationId)));

            log.info(
                    "Saved customer id={} identification={} active={} events={} correlationId={}",
                    saved.getId(),
                    saved.getIdentification(),
                    saved.isActive(),
                    events.size(),
                    correlationId);

            return mapper.toDomain(saved);
        } catch (DataIntegrityViolationException e) {
            // The authoritative uniqueness guard firing. The use case checked
            // first, but another request can commit between that check and this
            // insert — only the database sees both writes.
            log.warn(
                    "Rejected duplicate identification={} correlationId={}",
                    customer.identification(),
                    correlationId);
            throw new DuplicateIdentificationException(customer.identification());
        } catch (OptimisticLockingFailureException e) {
            // Somebody else updated this row between our read and our write.
            // Cheaper than holding a lock for the duration of the use case.
            log.warn("Optimistic lock conflict on customer id={} correlationId={}", customer.id(), correlationId);
            throw new ConcurrentModificationConflictException(customer.id());
        }
    }

    /**
     * {@code readOnly = true} is not decoration.
     *
     * <p>It tells Hibernate to skip dirty checking entirely — no snapshot of
     * every loaded entity, no comparison at flush time — and lets the driver
     * mark the transaction read-only, which some deployments route to a replica.
     * On a listing endpoint that loads a page of rows, that is a measurable
     * saving for one word.
     */
    @Transactional(readOnly = true)
    public Optional<Customer> findById(CustomerId id) {
        return customers.findById(id.value()).map(mapper::toDomain);
    }

    @Transactional(readOnly = true)
    public Optional<Customer> findByIdentification(String identification) {
        return customers.findByIdentification(identification).map(mapper::toDomain);
    }

    @Transactional(readOnly = true)
    public boolean existsByIdentification(String identification) {
        return customers.existsByIdentification(identification);
    }

    @Transactional(readOnly = true)
    public Page<CustomerEntity> search(String identification, Boolean active, int page, int size) {
        return customers.search(identification, active, PageRequest.of(page, size));
    }

    /** Exposed so the adapter can map without reaching for the mapper itself. */
    public Customer toDomain(CustomerEntity entity) {
        return mapper.toDomain(entity);
    }

    /** Test seam for asserting outbox contents; never used in production code. */
    @Transactional(readOnly = true)
    public long countOutboxEventsFor(UUID aggregateId) {
        return outbox.findAll().stream()
                .filter(event -> event.getAggregateId().equals(aggregateId))
                .count();
    }
}
