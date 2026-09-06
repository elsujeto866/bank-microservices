package com.bank.customer.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.bank.customer.application.command.RegisterCustomerCommand;
import com.bank.customer.application.exception.DuplicateIdentificationException;
import com.bank.customer.application.fake.FixedIdentifierGenerator;
import com.bank.customer.application.fake.InMemoryCustomerRepository;
import com.bank.customer.application.fake.StubPasswordHasher;
import com.bank.customer.domain.event.CustomerRegistered;
import com.bank.customer.domain.model.Address;
import com.bank.customer.domain.model.CustomerId;
import com.bank.customer.domain.model.Gender;
import com.bank.customer.domain.model.Identification;
import com.bank.customer.domain.model.PersonName;
import com.bank.customer.domain.model.PersonalData;
import com.bank.customer.domain.model.PhoneNumber;
import com.bank.customer.domain.model.PlainPassword;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

/**
 * Registering a customer.
 *
 * <p>Every collaborator is deterministic: a fixed {@link Clock}, a queue of
 * prepared identifiers and an instant hasher. Nothing here can produce a
 * different result on a different run, which is why the assertions can name
 * exact values instead of hedging.
 */
class RegisterCustomerServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-06T14:30:00Z");
    private static final CustomerId EXPECTED_ID =
            new CustomerId(UUID.fromString("3f1a8d2e-7c44-4b0a-9c6e-1f2b3c4d5e6f"));

    private InMemoryCustomerRepository customers;
    private RegisterCustomerService service;

    @BeforeEach
    void setUp() {
        customers = new InMemoryCustomerRepository();
        service = new RegisterCustomerService(
                customers,
                new StubPasswordHasher(),
                new FixedIdentifierGenerator(EXPECTED_ID),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static PersonalData profile(String identification) {
        return new PersonalData(
                new PersonName("Jose Lema"),
                Gender.MALE,
                new Identification(identification),
                new Address("Otavalo sn y principal"),
                new PhoneNumber("098254785"));
    }

    private static RegisterCustomerCommand command(String identification) {
        return new RegisterCustomerCommand(profile(identification), new PlainPassword("1234"), true);
    }

    @Test
    @DisplayName("persists the customer with the generated id and the fixed clock's instant")
    void registersCustomer() {
        StepVerifier.create(service.register(command("1712345678")))
                .assertNext(customer -> {
                    assertThat(customer.id()).isEqualTo(EXPECTED_ID);
                    assertThat(customer.createdAt()).isEqualTo(NOW);
                    assertThat(customer.updatedAt()).isEqualTo(NOW);
                    assertThat(customer.isActive()).isTrue();
                })
                .verifyComplete();

        assertThat(customers.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("stores the hashed password, never the plaintext")
    void hashesPassword() {
        StepVerifier.create(service.register(command("1712345678")))
                .assertNext(customer -> assertThat(customer.password().value())
                        .isEqualTo(StubPasswordHasher.PREFIX + "1234")
                        .doesNotStartWith("1234"))
                .verifyComplete();
    }

    @Test
    @DisplayName("hands the CustomerRegistered event to the repository on save")
    void publishesEvent() {
        StepVerifier.create(service.register(command("1712345678"))).expectNextCount(1).verifyComplete();

        // The events reached the outbox as part of the save. If save() failed,
        // neither the customer nor the event would exist — that atomicity is
        // the entire reason for the outbox pattern.
        assertThat(customers.publishedEvents())
                .singleElement()
                .isInstanceOfSatisfying(CustomerRegistered.class, event -> {
                    assertThat(event.customerId()).isEqualTo(EXPECTED_ID);
                    assertThat(event.occurredAt()).isEqualTo(NOW);
                    assertThat(event.profile().identification().value()).isEqualTo("1712345678");
                });
    }

    @Test
    @DisplayName("rejects a duplicate identification with a clear failure")
    void rejectsDuplicateIdentification() {
        InMemoryCustomerRepository shared = new InMemoryCustomerRepository();
        RegisterCustomerService first = new RegisterCustomerService(
                shared,
                new StubPasswordHasher(),
                new FixedIdentifierGenerator(EXPECTED_ID, new CustomerId(UUID.randomUUID())),
                Clock.fixed(NOW, ZoneOffset.UTC));

        StepVerifier.create(first.register(command("1712345678"))).expectNextCount(1).verifyComplete();

        StepVerifier.create(first.register(command("1712345678")))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(DuplicateIdentificationException.class)
                        .hasMessageContaining("1712345678"))
                .verify();

        assertThat(shared.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("normalises identification before checking uniqueness, so case cannot slip a duplicate through")
    void uniquenessIsCaseInsensitive() {
        InMemoryCustomerRepository shared = new InMemoryCustomerRepository();
        RegisterCustomerService svc = new RegisterCustomerService(
                shared,
                new StubPasswordHasher(),
                new FixedIdentifierGenerator(EXPECTED_ID, new CustomerId(UUID.randomUUID())),
                Clock.fixed(NOW, ZoneOffset.UTC));

        StepVerifier.create(svc.register(command("ab-123"))).expectNextCount(1).verifyComplete();

        // Identification upper-cases on construction, so "AB-123" and "ab-123"
        // are the same value long before the repository is asked. Without that
        // normalisation both rows would exist.
        StepVerifier.create(svc.register(command("AB-123")))
                .expectError(DuplicateIdentificationException.class)
                .verify();
    }

    @Test
    @DisplayName("does not consume an identifier when registration is rejected")
    void doesNotWasteIdentifierOnRejection() {
        InMemoryCustomerRepository shared = new InMemoryCustomerRepository();
        // Exactly one identifier is prepared. If the rejected second attempt
        // asked for one, the generator would throw and this test would fail —
        // which is how we know the duplicate check runs before anything else.
        RegisterCustomerService svc = new RegisterCustomerService(
                shared,
                new StubPasswordHasher(),
                new FixedIdentifierGenerator(EXPECTED_ID),
                Clock.fixed(NOW, ZoneOffset.UTC));

        StepVerifier.create(svc.register(command("1712345678"))).expectNextCount(1).verifyComplete();

        StepVerifier.create(svc.register(command("1712345678")))
                .expectError(DuplicateIdentificationException.class)
                .verify();
    }
}
