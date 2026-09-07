package com.bank.customer.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bank.customer.application.exception.ConcurrentModificationConflictException;
import com.bank.customer.application.exception.DuplicateIdentificationException;
import com.bank.customer.application.port.out.CustomerRepository;
import com.bank.customer.application.query.CustomerFilter;
import com.bank.customer.application.shared.PageRequest;
import com.bank.customer.domain.model.Address;
import com.bank.customer.domain.model.Customer;
import com.bank.customer.domain.model.CustomerId;
import com.bank.customer.domain.model.Gender;
import com.bank.customer.domain.model.HashedPassword;
import com.bank.customer.domain.model.Identification;
import com.bank.customer.domain.model.PersonName;
import com.bank.customer.domain.model.PersonalData;
import com.bank.customer.domain.model.PhoneNumber;
import com.bank.customer.infrastructure.persistence.repository.OutboxJpaRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.scheduler.Scheduler;
import reactor.test.StepVerifier;

/**
 * The persistence adapter against a real PostgreSQL.
 *
 * <p>Testcontainers, not H2. An in-memory database is a different database: it
 * has different types, a different SQL dialect, different constraint semantics
 * and, critically, no {@code jsonb}. Every interesting thing in this file —
 * that the unique constraint fires, that optimistic locking rejects a stale
 * write, that the outbox row commits with the customer — is behaviour of
 * PostgreSQL specifically. Testing it against something else proves the
 * something else works.
 *
 * <p>These are the tests the unit suite cannot write. The use-case tests run in
 * milliseconds against a fake and cover the business rules; this one starts a
 * container and covers the promises the adapter makes about the database.
 * Both are needed, and each is cheap only because the other exists.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class CustomerPersistenceIT {

    /**
     * A real PostgreSQL, started once for the whole class.
     *
     * <p>{@code @ServiceConnection} wires the container's JDBC url, username and
     * password into Spring automatically — no {@code @DynamicPropertySource}
     * block to keep in sync with the container definition.
     */
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private CustomerRepository customers;

    @Autowired
    private OutboxJpaRepository outbox;

    @Autowired
    private Scheduler jdbcScheduler;

    @Value("${spring.datasource.hikari.maximum-pool-size}")
    private int connectionPoolSize;

    private static Customer newCustomer(String identification) {
        return Customer.register(
                new CustomerId(UUID.randomUUID()),
                new PersonalData(
                        new PersonName("Jose Lema"),
                        Gender.MALE,
                        new Identification(identification),
                        new Address("Otavalo sn y principal"),
                        new PhoneNumber("098254785")),
                new HashedPassword("$2a$12$abcdefghijklmnopqrstuv"),
                true,
                Instant.parse("2026-09-06T14:30:00Z"));
    }

    @Test
    @DisplayName("the application context starts, which proves Flyway ran and Hibernate validated the schema")
    void contextStarts() {
        // Not a filler test. ddl-auto=validate means Hibernate compares every
        // mapping against the schema Flyway produced and refuses to start on a
        // mismatch. A misspelled column or a wrong type fails right here,
        // rather than on the first request that happens to touch it.
        assertThat(customers).isNotNull();
    }

    @Test
    @DisplayName("saves and reads back a customer through the port")
    void savesAndReads() {
        Customer customer = newCustomer("1712345678");

        StepVerifier.create(customers.save(customer)).expectNextCount(1).verifyComplete();

        StepVerifier.create(customers.findById(customer.id()))
                .assertNext(found -> {
                    assertThat(found.id()).isEqualTo(customer.id());
                    assertThat(found.name().value()).isEqualTo("Jose Lema");
                    assertThat(found.identification().value()).isEqualTo("1712345678");
                    assertThat(found.isActive()).isTrue();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("rehydrated customers carry no pending events")
    void rehydratedCustomerHasNoEvents() {
        Customer customer = newCustomer("1712345679");
        StepVerifier.create(customers.save(customer)).expectNextCount(1).verifyComplete();

        StepVerifier.create(customers.findById(customer.id()))
                // If the mapper used register() instead of rehydrate(), every
                // read would manufacture a CustomerRegistered event for a
                // customer created months ago — and the outbox would publish it.
                .assertNext(found -> assertThat(found.pendingEvents()).isEmpty())
                .verifyComplete();
    }

    @Test
    @DisplayName("writes the domain event to the outbox in the same transaction as the customer")
    void writesOutboxAtomically() {
        Customer customer = newCustomer("1712345680");

        StepVerifier.create(customers.save(customer)).expectNextCount(1).verifyComplete();

        assertThat(outbox.findAll())
                .filteredOn(event -> event.getAggregateId().equals(customer.id().value()))
                .singleElement()
                .satisfies(event -> {
                    // The contract's name, not the domain's class name. The
                    // domain says CustomerRegistered; customer.events.v1
                    // promised CustomerCreated.
                    assertThat(event.getEventType()).isEqualTo("CustomerCreated");
                    assertThat(event.getAggregateType()).isEqualTo("Customer");
                    assertThat(event.getPublishedAt()).isNull();
                    assertThat(event.getOccurredAt()).isEqualTo(Instant.parse("2026-09-06T14:30:00Z"));
                    assertThat(event.getPayload())
                            .contains("\"eventType\":\"CustomerCreated\"")
                            .contains("\"identification\":\"1712345680\"")
                            // The credential is not in the payload and cannot be:
                            // PersonalData does not carry one.
                            .doesNotContain("password")
                            .doesNotContain("$2a$12$");
                });
    }

    @Test
    @DisplayName("the unique constraint rejects a duplicate identification the pre-check could not see")
    void rejectsDuplicateIdentification() {
        StepVerifier.create(customers.save(newCustomer("1712345681")))
                .expectNextCount(1)
                .verifyComplete();

        // Saved directly through the port, bypassing the use case's pre-check.
        // This is the race the check cannot close: only the database sees both
        // writes, so only the database can settle it.
        StepVerifier.create(customers.save(newCustomer("1712345681")))
                .expectError(DuplicateIdentificationException.class)
                .verify();
    }

    @Test
    @DisplayName("optimistic locking rejects a write based on stale state")
    void rejectsStaleWrite() {
        Customer customer = newCustomer("1712345682");
        StepVerifier.create(customers.save(customer)).expectNextCount(1).verifyComplete();

        // Two independent reads of the same row: two callers, each holding
        // version N. No lock was taken, so both proceed.
        Customer first = customers.findById(customer.id()).block();
        Customer second = customers.findById(customer.id()).block();
        assertThat(first).isNotNull();
        assertThat(second).isNotNull();

        first.updateProfile(
                new PersonalData(
                        new PersonName("First Writer Wins"),
                        Gender.MALE,
                        new Identification("1712345682"),
                        new Address("Otavalo sn y principal"),
                        new PhoneNumber("098254785")),
                Instant.parse("2026-09-07T09:00:00Z"));
        StepVerifier.create(customers.save(first)).expectNextCount(1).verifyComplete();

        second.updateProfile(
                new PersonalData(
                        new PersonName("Second Writer Loses"),
                        Gender.MALE,
                        new Identification("1712345682"),
                        new Address("Otavalo sn y principal"),
                        new PhoneNumber("098254785")),
                Instant.parse("2026-09-07T09:00:01Z"));

        // The row is now at version N+1, so the second write matches zero rows.
        // Without this column, it would silently overwrite the first writer's
        // change — a lost update, with nothing to find afterwards.
        StepVerifier.create(customers.save(second))
                .expectError(ConcurrentModificationConflictException.class)
                .verify();

        StepVerifier.create(customers.findById(customer.id()))
                .assertNext(found -> assertThat(found.name().value()).isEqualTo("First Writer Wins"))
                .verifyComplete();
    }

    @Test
    @DisplayName("filters and paginates through the port, reporting the total across all pages")
    void filtersAndPaginates() {
        Customer active = newCustomer("1712345683");
        StepVerifier.create(customers.save(active)).expectNextCount(1).verifyComplete();

        StepVerifier.create(customers.findAll(
                        CustomerFilter.byIdentification(new Identification("1712345683")), new PageRequest(0, 10)))
                .assertNext(page -> {
                    assertThat(page.content()).hasSize(1);
                    assertThat(page.totalElements()).isEqualTo(1);
                })
                .verifyComplete();

        StepVerifier.create(
                        customers.findAll(new CustomerFilter(Optional.empty(), Optional.of(false)), new PageRequest(0, 10)))
                .assertNext(page -> assertThat(page.content()).isEmpty())
                .verifyComplete();
    }

    @Test
    @DisplayName("the JDBC scheduler is sized to the connection pool, as ADR-0003 requires")
    void schedulerMatchesConnectionPool() {
        // The promise in ADR-0003 that is easiest to make and easiest to break.
        // If somebody raises the Hikari pool without touching the scheduler,
        // threads queue on connections instead of on queries and the thread dump
        // blames the wrong thing. Wiring both to one property makes that
        // impossible — and this test proves the wiring, not just the intent.
        assertThat(jdbcScheduler).isNotNull();
        assertThat(connectionPoolSize).isPositive();
        assertThat(jdbcScheduler.toString()).contains(String.valueOf(connectionPoolSize));
    }

    @Test
    @DisplayName("stores identification upper-cased, so the unique index actually catches case variants")
    void normalisesIdentification() {
        Customer customer = Customer.register(
                new CustomerId(UUID.randomUUID()),
                new PersonalData(
                        new PersonName("Marianela Montalvo"),
                        Gender.FEMALE,
                        new Identification("ab-9999"),
                        new Address("Amazonas y NNUU"),
                        new PhoneNumber("097548965")),
                new HashedPassword("$2a$12$abcdefghijklmnopqrstuv"),
                true,
                Instant.parse("2026-09-06T14:30:00Z"));

        StepVerifier.create(customers.save(customer)).expectNextCount(1).verifyComplete();

        StepVerifier.create(customers.findByIdentification(new Identification("AB-9999")))
                .expectNextCount(1)
                .verifyComplete();
    }
}
