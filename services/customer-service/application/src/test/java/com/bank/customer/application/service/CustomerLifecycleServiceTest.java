package com.bank.customer.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.bank.customer.application.command.PatchCustomerCommand;
import com.bank.customer.application.command.ReplaceCustomerCommand;
import com.bank.customer.application.exception.CustomerNotFoundException;
import com.bank.customer.application.exception.ImmutableIdentificationException;
import com.bank.customer.application.fake.InMemoryCustomerRepository;
import com.bank.customer.application.fake.StubPasswordHasher;
import com.bank.customer.application.query.CustomerFilter;
import com.bank.customer.application.shared.PageRequest;
import com.bank.customer.domain.event.CustomerDeactivated;
import com.bank.customer.domain.event.CustomerProfileUpdated;
import com.bank.customer.domain.exception.CustomerAlreadyInactiveException;
import com.bank.customer.domain.model.Address;
import com.bank.customer.domain.model.Customer;
import com.bank.customer.domain.model.CustomerId;
import com.bank.customer.domain.model.Gender;
import com.bank.customer.domain.model.HashedPassword;
import com.bank.customer.domain.model.Identification;
import com.bank.customer.domain.model.PersonName;
import com.bank.customer.domain.model.PersonalData;
import com.bank.customer.domain.model.PhoneNumber;
import com.bank.customer.domain.model.PlainPassword;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

/** Reading, updating and deactivating an existing customer. */
class CustomerLifecycleServiceTest {

    private static final Instant CREATED = Instant.parse("2026-09-06T14:30:00Z");
    private static final Instant NOW = Instant.parse("2026-09-07T09:00:00Z");
    private static final CustomerId ID = new CustomerId(UUID.fromString("3f1a8d2e-7c44-4b0a-9c6e-1f2b3c4d5e6f"));
    private static final CustomerId MISSING_ID = new CustomerId(UUID.fromString("00000000-0000-4000-8000-000000000000"));
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private InMemoryCustomerRepository customers;

    @BeforeEach
    void setUp() {
        customers = new InMemoryCustomerRepository();
    }

    private static PersonalData profile(String name, String identification, String phone) {
        return new PersonalData(
                new PersonName(name),
                Gender.MALE,
                new Identification(identification),
                new Address("Otavalo sn y principal"),
                new PhoneNumber(phone));
    }

    private Customer seedActiveCustomer() {
        Customer customer = Customer.rehydrate(
                ID,
                profile("Jose Lema", "1712345678", "098254785"),
                new HashedPassword("hashed:1234"),
                true,
                CREATED,
                CREATED);
        customers.seed(customer);
        return customer;
    }

    @Nested
    @DisplayName("GetCustomerService")
    class Get {

        @Test
        @DisplayName("returns the customer when it exists")
        void returnsCustomer() {
            seedActiveCustomer();

            StepVerifier.create(new GetCustomerService(customers).byId(ID))
                    .assertNext(customer -> assertThat(customer.id()).isEqualTo(ID))
                    .verifyComplete();
        }

        @Test
        @DisplayName("fails instead of completing empty, so the boundary cannot answer 200 with no body")
        void failsWhenMissing() {
            StepVerifier.create(new GetCustomerService(customers).byId(MISSING_ID))
                    .expectError(CustomerNotFoundException.class)
                    .verify();
        }
    }

    @Nested
    @DisplayName("ListCustomersService")
    class List {

        @Test
        @DisplayName("filters by active flag and reports the total across all pages")
        void filtersAndPages() {
            seedActiveCustomer();
            Customer inactive = Customer.rehydrate(
                    new CustomerId(UUID.randomUUID()),
                    profile("Juan Osorio", "1798765432", "098874587"),
                    new HashedPassword("hashed:1245"),
                    false,
                    CREATED,
                    CREATED);
            customers.seed(inactive);

            StepVerifier.create(new ListCustomersService(customers)
                            .list(new CustomerFilter(Optional.empty(), Optional.of(true)), new PageRequest(0, 1)))
                    .assertNext(page -> {
                        assertThat(page.content()).hasSize(1);
                        assertThat(page.totalElements()).isEqualTo(1);
                        assertThat(page.totalPages()).isEqualTo(1);
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("DeactivateCustomerService")
    class Deactivate {

        @Test
        @DisplayName("deactivates the customer and records the event")
        void deactivates() {
            seedActiveCustomer();

            StepVerifier.create(new DeactivateCustomerService(customers, CLOCK).deactivate(ID))
                    .verifyComplete();

            StepVerifier.create(customers.findById(ID))
                    .assertNext(customer -> {
                        assertThat(customer.isActive()).isFalse();
                        assertThat(customer.updatedAt()).isEqualTo(NOW);
                    })
                    .verifyComplete();

            assertThat(customers.publishedEvents()).singleElement().isInstanceOf(CustomerDeactivated.class);
        }

        @Test
        @DisplayName("fails when the customer does not exist")
        void failsWhenMissing() {
            StepVerifier.create(new DeactivateCustomerService(customers, CLOCK).deactivate(MISSING_ID))
                    .expectError(CustomerNotFoundException.class)
                    .verify();
        }

        @Test
        @DisplayName("lets the domain rule reject a second deactivation rather than re-checking here")
        void secondDeactivationFails() {
            seedActiveCustomer();
            DeactivateCustomerService service = new DeactivateCustomerService(customers, CLOCK);

            StepVerifier.create(service.deactivate(ID)).verifyComplete();

            // The service contains no `if (!customer.isActive())`. Duplicating
            // the rule here would create a second place to keep in sync.
            StepVerifier.create(service.deactivate(ID))
                    .expectError(CustomerAlreadyInactiveException.class)
                    .verify();
        }
    }

    @Nested
    @DisplayName("PatchCustomerService")
    class Patch {

        private PatchCustomerService service() {
            return new PatchCustomerService(customers, new StubPasswordHasher(), CLOCK);
        }

        @Test
        @DisplayName("changes only the supplied field and leaves the rest alone")
        void patchesOneField() {
            seedActiveCustomer();

            PatchCustomerCommand command = new PatchCustomerCommand(
                    ID,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.of(new Address("Amazonas y NNUU")),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty());

            StepVerifier.create(service().patch(command))
                    .assertNext(customer -> {
                        assertThat(customer.address().value()).isEqualTo("Amazonas y NNUU");
                        // Not wiped. This is the defect the Optional-based command exists to prevent.
                        assertThat(customer.name().value()).isEqualTo("Jose Lema");
                        assertThat(customer.phone().value()).isEqualTo("098254785");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("never changes identification, even though it is part of the profile")
        void neverChangesIdentification() {
            seedActiveCustomer();

            PatchCustomerCommand command = new PatchCustomerCommand(
                    ID,
                    Optional.of(new PersonName("Jose Lema Torres")),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty());

            StepVerifier.create(service().patch(command))
                    .assertNext(customer -> {
                        assertThat(customer.name().value()).isEqualTo("Jose Lema Torres");
                        assertThat(customer.identification().value()).isEqualTo("1712345678");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("emits no profile event when only the active flag changes")
        void statusOnlyPatchEmitsNoProfileUpdate() {
            seedActiveCustomer();

            PatchCustomerCommand command = new PatchCustomerCommand(
                    ID,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.of(false));

            StepVerifier.create(service().patch(command)).expectNextCount(1).verifyComplete();

            // One event, and it describes what actually happened. An event
            // announcing a profile change that never occurred would corrupt
            // every read model that trusts it.
            assertThat(customers.publishedEvents())
                    .singleElement()
                    .isInstanceOf(CustomerDeactivated.class);
        }

        @Test
        @DisplayName("hashes a supplied password")
        void hashesNewPassword() {
            seedActiveCustomer();

            PatchCustomerCommand command = new PatchCustomerCommand(
                    ID,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.of(new PlainPassword("newSecret")),
                    Optional.empty());

            StepVerifier.create(service().patch(command))
                    .assertNext(customer ->
                            assertThat(customer.password().value()).isEqualTo(StubPasswordHasher.PREFIX + "newSecret"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("fails when the customer does not exist")
        void failsWhenMissing() {
            PatchCustomerCommand command = new PatchCustomerCommand(
                    MISSING_ID,
                    Optional.of(new PersonName("Nobody At All")),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty());

            StepVerifier.create(service().patch(command))
                    .expectError(CustomerNotFoundException.class)
                    .verify();
        }
    }

    @Nested
    @DisplayName("ReplaceCustomerService")
    class Replace {

        private ReplaceCustomerService service() {
            return new ReplaceCustomerService(customers, new StubPasswordHasher(), CLOCK);
        }

        @Test
        @DisplayName("replaces every mutable attribute")
        void replacesProfile() {
            seedActiveCustomer();

            ReplaceCustomerCommand command = new ReplaceCustomerCommand(
                    ID, profile("Jose Lema Torres", "1712345678", "0987654321"), Optional.empty(), true);

            StepVerifier.create(service().replace(command))
                    .assertNext(customer -> {
                        assertThat(customer.name().value()).isEqualTo("Jose Lema Torres");
                        assertThat(customer.phone().value()).isEqualTo("0987654321");
                        assertThat(customer.updatedAt()).isEqualTo(NOW);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("reports an attempt to change identification instead of ignoring it")
        void rejectsIdentificationChange() {
            seedActiveCustomer();

            ReplaceCustomerCommand command = new ReplaceCustomerCommand(
                    ID, profile("Jose Lema", "1798765432", "098254785"), Optional.empty(), true);

            // The domain makes identification final, so accepting this request
            // and answering 200 would tell the caller a change succeeded when
            // nothing happened. A silent no-op is worse than an error.
            StepVerifier.create(service().replace(command))
                    .expectErrorSatisfies(error -> assertThat(error)
                            .isInstanceOf(ImmutableIdentificationException.class)
                            .hasMessageContaining("1798765432"))
                    .verify();
        }

        @Test
        @DisplayName("succeeds when active:false is resent for an already inactive customer")
        void repeatedInactiveReplaceSucceeds() {
            Customer customer = Customer.rehydrate(
                    ID,
                    profile("Jose Lema", "1712345678", "098254785"),
                    new HashedPassword("hashed:1234"),
                    false,
                    CREATED,
                    CREATED);
            customers.seed(customer);

            ReplaceCustomerCommand command = new ReplaceCustomerCommand(
                    ID, profile("Jose Lema", "1712345678", "098254785"), Optional.empty(), false);

            // deactivate() rejects a repeat, but a PUT that merely agrees with
            // reality is not the stale-state case that rule guards against.
            StepVerifier.create(service().replace(command))
                    .assertNext(replaced -> assertThat(replaced.isActive()).isFalse())
                    .verifyComplete();
        }

        @Test
        @DisplayName("leaves the stored password untouched when none is supplied")
        void keepsPasswordWhenAbsent() {
            seedActiveCustomer();

            ReplaceCustomerCommand command = new ReplaceCustomerCommand(
                    ID, profile("Jose Lema Torres", "1712345678", "098254785"), Optional.empty(), true);

            StepVerifier.create(service().replace(command))
                    .assertNext(customer -> assertThat(customer.password().value()).isEqualTo("hashed:1234"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("records a profile update event")
        void recordsEvent() {
            seedActiveCustomer();

            ReplaceCustomerCommand command = new ReplaceCustomerCommand(
                    ID, profile("Jose Lema Torres", "1712345678", "098254785"), Optional.empty(), true);

            StepVerifier.create(service().replace(command)).expectNextCount(1).verifyComplete();

            assertThat(customers.publishedEvents())
                    .hasAtLeastOneElementOfType(CustomerProfileUpdated.class);
        }
    }
}
