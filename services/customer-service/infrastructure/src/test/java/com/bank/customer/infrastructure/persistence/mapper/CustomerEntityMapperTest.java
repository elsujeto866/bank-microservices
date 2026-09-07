package com.bank.customer.infrastructure.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.bank.customer.domain.model.Address;
import com.bank.customer.domain.model.Customer;
import com.bank.customer.domain.model.CustomerId;
import com.bank.customer.domain.model.Gender;
import com.bank.customer.domain.model.HashedPassword;
import com.bank.customer.domain.model.Identification;
import com.bank.customer.domain.model.PersonName;
import com.bank.customer.domain.model.PersonalData;
import com.bank.customer.domain.model.PhoneNumber;
import com.bank.customer.infrastructure.persistence.entity.CustomerEntity;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The translation between the domain model and the persistence model. */
class CustomerEntityMapperTest {

    private static final CustomerId ID = new CustomerId(UUID.fromString("3f1a8d2e-7c44-4b0a-9c6e-1f2b3c4d5e6f"));
    private static final Instant CREATED = Instant.parse("2026-09-06T14:30:00Z");
    private static final Instant UPDATED = Instant.parse("2026-09-07T09:00:00Z");

    private final CustomerEntityMapper mapper = new CustomerEntityMapper();

    private static Customer customer() {
        return Customer.rehydrate(
                ID,
                new PersonalData(
                        new PersonName("Jose Lema"),
                        Gender.MALE,
                        new Identification("1712345678"),
                        new Address("Otavalo sn y principal"),
                        new PhoneNumber("098254785")),
                new HashedPassword("$2a$12$hash"),
                true,
                CREATED,
                UPDATED);
    }

    @Test
    @DisplayName("round-trips a customer without losing anything")
    void roundTrips() {
        Customer original = customer();

        Customer restored = mapper.toDomain(mapper.toEntity(original));

        assertThat(restored.id()).isEqualTo(original.id());
        assertThat(restored.name().value()).isEqualTo("Jose Lema");
        assertThat(restored.gender()).isEqualTo(Gender.MALE);
        assertThat(restored.identification().value()).isEqualTo("1712345678");
        assertThat(restored.address().value()).isEqualTo("Otavalo sn y principal");
        assertThat(restored.phone().value()).isEqualTo("098254785");
        assertThat(restored.password().value()).isEqualTo("$2a$12$hash");
        assertThat(restored.isActive()).isTrue();
        assertThat(restored.createdAt()).isEqualTo(CREATED);
        assertThat(restored.updatedAt()).isEqualTo(UPDATED);
    }

    @Test
    @DisplayName("rehydrates rather than registers, so reading a row raises no event")
    void readingRaisesNoEvent() {
        Customer restored = mapper.toDomain(mapper.toEntity(customer()));

        // If this mapper called Customer.register(), every read would
        // manufacture a CustomerRegistered event for a customer created months
        // ago — and the outbox would publish it to every consumer.
        assertThat(restored.pendingEvents()).isEmpty();
    }

    @Test
    @DisplayName("never copies the version, which belongs to Hibernate alone")
    void doesNotTouchVersion() {
        CustomerEntity managed = new CustomerEntity();
        managed.setId(ID.value());
        managed.setCreatedAt(CREATED);
        managed.setVersion(7L);

        mapper.applyTo(managed, customer());

        // Optimistic locking is a persistence strategy, not a business rule.
        // The aggregate has never heard of a version, and copying a stale one
        // here would overwrite the value Hibernate is tracking — quietly
        // disabling the very protection the column exists for.
        assertThat(managed.getVersion()).isEqualTo(7L);
    }

    @Test
    @DisplayName("applyTo leaves createdAt alone while moving updatedAt")
    void preservesCreatedAt() {
        CustomerEntity managed = mapper.toEntity(customer());
        Instant originalCreatedAt = managed.getCreatedAt();

        mapper.applyTo(managed, customer());

        assertThat(managed.getCreatedAt()).isEqualTo(originalCreatedAt);
        assertThat(managed.getUpdatedAt()).isEqualTo(UPDATED);
    }

    @Test
    @DisplayName("maps gender by name, so the persisted value survives a reordered enum")
    void mapsGenderByName() {
        CustomerEntity entity = mapper.toEntity(customer());

        // Paired with @Enumerated(STRING) on the entity. Ordinal mapping would
        // store a position, and inserting a constant into the middle of the enum
        // would silently rewrite the meaning of every existing row.
        assertThat(entity.getGender()).isEqualTo(CustomerEntity.GenderCode.MALE);
        assertThat(entity.getGender().name()).isEqualTo(Gender.MALE.name());
    }
}
