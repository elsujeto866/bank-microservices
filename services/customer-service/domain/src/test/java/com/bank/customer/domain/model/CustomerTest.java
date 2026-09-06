package com.bank.customer.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bank.customer.domain.event.CustomerDeactivated;
import com.bank.customer.domain.event.CustomerProfileUpdated;
import com.bank.customer.domain.event.CustomerRegistered;
import com.bank.customer.domain.exception.CustomerAlreadyInactiveException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Behaviour of the customer aggregate.
 *
 * <p>No Spring context. No database. No mocking framework. No {@code sleep}.
 * These tests run in single-digit milliseconds because the aggregate is a pure
 * function of its inputs — the id and every timestamp are passed in.
 *
 * <p>That speed is not a vanity metric. A suite that takes thirty seconds gets
 * run before a commit; one that takes eight minutes gets run by CI, after the
 * mistake is already pushed.
 */
class CustomerTest {

    private static final CustomerId ID = new CustomerId(UUID.fromString("3f1a8d2e-7c44-4b0a-9c6e-1f2b3c4d5e6f"));
    private static final Instant NOW = Instant.parse("2026-09-06T14:30:00Z");
    private static final Instant LATER = Instant.parse("2026-09-07T09:00:00Z");
    private static final HashedPassword PASSWORD = new HashedPassword("$2a$10$hashed");

    private static PersonalData profileOf(String name, String phone) {
        return new PersonalData(
                new PersonName(name),
                Gender.MALE,
                new Identification("1712345678"),
                new Address("Otavalo sn y principal"),
                new PhoneNumber(phone));
    }

    private static Customer activeCustomer() {
        return Customer.register(ID, profileOf("Jose Lema", "098254785"), PASSWORD, true, NOW);
    }

    @Nested
    @DisplayName("register")
    class Register {

        @Test
        @DisplayName("creates an active customer with matching timestamps")
        void createsCustomer() {
            Customer customer = activeCustomer();

            assertThat(customer.id()).isEqualTo(ID);
            assertThat(customer.name().value()).isEqualTo("Jose Lema");
            assertThat(customer.isActive()).isTrue();
            assertThat(customer.createdAt()).isEqualTo(NOW);
            assertThat(customer.updatedAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("records a CustomerRegistered event carrying the full profile")
        void recordsEvent() {
            Customer customer = activeCustomer();

            assertThat(customer.pendingEvents())
                    .singleElement()
                    .isInstanceOfSatisfying(CustomerRegistered.class, event -> {
                        assertThat(event.customerId()).isEqualTo(ID);
                        assertThat(event.occurredAt()).isEqualTo(NOW);
                        assertThat(event.active()).isTrue();
                        // The whole profile travels with the event, so a consumer
                        // never has to call back to build its read model.
                        assertThat(event.profile().name().value()).isEqualTo("Jose Lema");
                        assertThat(event.profile().identification().value()).isEqualTo("1712345678");
                    });
        }
    }

    @Nested
    @DisplayName("rehydrate")
    class Rehydrate {

        @Test
        @DisplayName("records no event, because loading a row is not a business fact")
        void recordsNoEvent() {
            Customer customer = Customer.rehydrate(
                    ID, profileOf("Jose Lema", "098254785"), PASSWORD, true, NOW, LATER);

            assertThat(customer.pendingEvents()).isEmpty();
            assertThat(customer.createdAt()).isEqualTo(NOW);
            assertThat(customer.updatedAt()).isEqualTo(LATER);
        }
    }

    @Nested
    @DisplayName("updateProfile")
    class UpdateProfile {

        @Test
        @DisplayName("changes the mutable attributes and moves updatedAt")
        void updatesProfile() {
            Customer customer = activeCustomer();

            customer.updateProfile(profileOf("Jose Lema Torres", "0987654321"), LATER);

            assertThat(customer.name().value()).isEqualTo("Jose Lema Torres");
            assertThat(customer.phone().value()).isEqualTo("0987654321");
            assertThat(customer.updatedAt()).isEqualTo(LATER);
            assertThat(customer.createdAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("records the new full state, not a delta")
        void recordsSnapshotEvent() {
            Customer customer = activeCustomer();
            customer.pullEvents();

            customer.updateProfile(profileOf("Jose Lema Torres", "0987654321"), LATER);

            assertThat(customer.pendingEvents())
                    .singleElement()
                    .isInstanceOfSatisfying(CustomerProfileUpdated.class, event -> {
                        assertThat(event.profile().name().value()).isEqualTo("Jose Lema Torres");
                        assertThat(event.profile().address().value()).isEqualTo("Otavalo sn y principal");
                        assertThat(event.occurredAt()).isEqualTo(LATER);
                    });
        }
    }

    @Nested
    @DisplayName("deactivate")
    class Deactivate {

        @Test
        @DisplayName("marks the customer inactive and records the event")
        void deactivates() {
            Customer customer = activeCustomer();
            customer.pullEvents();

            customer.deactivate(LATER);

            assertThat(customer.isActive()).isFalse();
            assertThat(customer.updatedAt()).isEqualTo(LATER);
            assertThat(customer.pendingEvents())
                    .singleElement()
                    .isInstanceOfSatisfying(CustomerDeactivated.class, event -> {
                        assertThat(event.customerId()).isEqualTo(ID);
                        assertThat(event.occurredAt()).isEqualTo(LATER);
                    });
        }

        @Test
        @DisplayName("rejects a second deactivation instead of silently agreeing")
        void rejectsSecondDeactivation() {
            Customer customer = activeCustomer();
            customer.deactivate(LATER);

            assertThatThrownBy(() -> customer.deactivate(LATER))
                    .isInstanceOf(CustomerAlreadyInactiveException.class)
                    .hasMessageContaining(ID.toString());
        }

        @Test
        @DisplayName("records no extra event when the second attempt fails")
        void failedAttemptRecordsNothing() {
            Customer customer = activeCustomer();
            customer.pullEvents();
            customer.deactivate(LATER);

            assertThatThrownBy(() -> customer.deactivate(LATER))
                    .isInstanceOf(CustomerAlreadyInactiveException.class);

            // One event, from the deactivation that actually happened. A rejected
            // operation must leave no trace — an event for something that did not
            // occur is worse than no event at all.
            assertThat(customer.pendingEvents()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("activate")
    class Activate {

        @Test
        @DisplayName("reactivates an inactive customer")
        void reactivates() {
            Customer customer = activeCustomer();
            customer.deactivate(NOW);
            customer.pullEvents();

            customer.activate(LATER);

            assertThat(customer.isActive()).isTrue();
            assertThat(customer.pendingEvents()).singleElement().isInstanceOf(CustomerProfileUpdated.class);
        }

        @Test
        @DisplayName("is idempotent on an already active customer")
        void isIdempotent() {
            Customer customer = activeCustomer();
            customer.pullEvents();

            customer.activate(LATER);

            // Unlike deactivate. Asking for a state that already holds satisfies
            // the caller's intent and hides nothing, so agreeing is safe.
            assertThat(customer.isActive()).isTrue();
            assertThat(customer.updatedAt()).isEqualTo(NOW);
            assertThat(customer.pendingEvents()).isEmpty();
        }
    }

    @Nested
    @DisplayName("pullEvents")
    class PullEvents {

        @Test
        @DisplayName("drains the recorded events so they cannot be published twice")
        void drains() {
            Customer customer = activeCustomer();

            assertThat(customer.pullEvents()).hasSize(1);
            assertThat(customer.pullEvents()).isEmpty();
            assertThat(customer.pendingEvents()).isEmpty();
        }
    }

    @Nested
    @DisplayName("identity")
    class Identity {

        @Test
        @DisplayName("two customers with the same id are equal even with different attributes")
        void equalById() {
            Customer one = Customer.register(ID, profileOf("Jose Lema", "098254785"), PASSWORD, true, NOW);
            Customer two = Customer.register(ID, profileOf("Someone Else", "0999999999"), PASSWORD, false, LATER);

            // An entity is its identity. The same customer with a new address is
            // still the same customer — comparing by attributes gets that wrong.
            assertThat(one).isEqualTo(two).hasSameHashCodeAs(two);
        }

        @Test
        @DisplayName("two customers with identical attributes but different ids are not equal")
        void differentIdsAreNotEqual() {
            PersonalData sameProfile = profileOf("Jose Lema", "098254785");
            Customer one = Customer.register(ID, sameProfile, PASSWORD, true, NOW);
            Customer two = Customer.register(new CustomerId(UUID.randomUUID()), sameProfile, PASSWORD, true, NOW);

            assertThat(one).isNotEqualTo(two);
        }
    }

    @Nested
    @DisplayName("toString")
    class ToStringBehaviour {

        @Test
        @DisplayName("never exposes the password")
        void hidesPassword() {
            Customer customer = activeCustomer();

            assertThat(customer.toString()).doesNotContain("hashed");
            assertThat(customer.password().toString()).isEqualTo("HashedPassword[****]");
        }
    }
}
