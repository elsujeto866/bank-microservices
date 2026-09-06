package com.bank.customer.application.fake;

import com.bank.customer.application.exception.DuplicateIdentificationException;
import com.bank.customer.application.port.out.CustomerRepository;
import com.bank.customer.application.query.CustomerFilter;
import com.bank.customer.application.shared.Page;
import com.bank.customer.application.shared.PageRequest;
import com.bank.customer.domain.event.CustomerEvent;
import com.bank.customer.domain.model.Customer;
import com.bank.customer.domain.model.CustomerId;
import com.bank.customer.domain.model.Identification;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * A hand-written in-memory implementation of {@link CustomerRepository}.
 *
 * <p><strong>A fake, not a mock, and the difference matters.</strong>
 *
 * <p>A mocked repository is told what to return:
 * {@code when(repo.findById(id)).thenReturn(Mono.just(customer))}. The test
 * then asserts on the answer the test itself supplied — it verifies that the
 * code called the method, which is a restatement of the implementation. Rename
 * a method and every such test needs editing without a single behaviour having
 * changed.
 *
 * <p>A fake has real behaviour. Save a customer and you can find it. Save two
 * with the same identification and the second one fails, exactly as the unique
 * constraint will. Tests then read as scenarios — "given a registered customer,
 * when I deactivate them, then…" — instead of as a script of stubbed calls.
 *
 * <p>Mockito still earns its place, but for a narrower job: proving an
 * interaction happened at all (an event was published, a notification was sent)
 * or forcing a failure that is awkward to reach otherwise.
 *
 * <p>This fake also mirrors the one behaviour of the real adapter the use cases
 * depend on: {@link #save} <strong>drains</strong> the aggregate's pending
 * events into {@link #publishedEvents()}, the way the real implementation
 * drains them into the outbox table inside the same transaction (ADR-0005).
 * Without that, a test could not tell whether the events survived the save.
 */
public class InMemoryCustomerRepository implements CustomerRepository {

    private final Map<CustomerId, Customer> stored = new LinkedHashMap<>();
    private final List<CustomerEvent> publishedEvents = new ArrayList<>();

    @Override
    public Mono<Customer> save(Customer customer) {
        return Mono.fromCallable(() -> {
            boolean identificationTakenByAnother = stored.values().stream()
                    .anyMatch(existing -> existing.identification().equals(customer.identification())
                            && !existing.id().equals(customer.id()));
            if (identificationTakenByAnother) {
                // What the unique constraint does in the real database. Modelled
                // here so the race the pre-check cannot close is still covered.
                throw new DuplicateIdentificationException(customer.identification());
            }
            publishedEvents.addAll(customer.pullEvents());
            stored.put(customer.id(), customer);
            return customer;
        });
    }

    @Override
    public Mono<Customer> findById(CustomerId id) {
        return Mono.justOrEmpty(stored.get(id));
    }

    @Override
    public Mono<Customer> findByIdentification(Identification identification) {
        return Mono.justOrEmpty(stored.values().stream()
                .filter(customer -> customer.identification().equals(identification))
                .findFirst());
    }

    @Override
    public Mono<Boolean> existsByIdentification(Identification identification) {
        return Mono.just(stored.values().stream()
                .anyMatch(customer -> customer.identification().equals(identification)));
    }

    @Override
    public Mono<Page<Customer>> findAll(CustomerFilter filter, PageRequest pageRequest) {
        List<Customer> matching = stored.values().stream()
                .filter(customer -> filter.identification()
                        .map(wanted -> customer.identification().equals(wanted))
                        .orElse(true))
                .filter(customer ->
                        filter.active().map(wanted -> customer.isActive() == wanted).orElse(true))
                .sorted(Comparator.comparing(Customer::createdAt))
                .toList();

        List<Customer> pageContent = matching.stream()
                .skip(pageRequest.offset())
                .limit(pageRequest.size())
                .toList();

        return Mono.just(new Page<>(pageContent, pageRequest.page(), pageRequest.size(), matching.size()));
    }

    /** Events handed over during {@link #save}, in order. */
    public List<CustomerEvent> publishedEvents() {
        return List.copyOf(publishedEvents);
    }

    /** Seeds state without going through a use case, for arranging a scenario. */
    public void seed(Customer customer) {
        customer.pullEvents();
        stored.put(customer.id(), customer);
    }

    public int count() {
        return stored.size();
    }
}
