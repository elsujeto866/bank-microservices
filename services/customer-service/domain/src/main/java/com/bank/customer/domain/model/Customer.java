package com.bank.customer.domain.model;

import com.bank.customer.domain.event.CustomerDeactivated;
import com.bank.customer.domain.event.CustomerEvent;
import com.bank.customer.domain.event.CustomerProfileUpdated;
import com.bank.customer.domain.event.CustomerRegistered;
import com.bank.customer.domain.exception.CustomerAlreadyInactiveException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The customer aggregate — the consistency boundary of this service.
 *
 * <p>Every rule about what a customer may become lives inside this class. There
 * is no public constructor and there are no setters; state changes only through
 * methods that name the business operation being performed. Ask yourself what
 * {@code customer.setActive(false)} means and you get no answer. Ask what
 * {@code customer.deactivate(now)} means and you do — and it is the only place
 * that can reject a second deactivation.
 *
 * <p><strong>Nothing here knows about time, randomness or persistence.</strong>
 * The identifier and every timestamp are handed in by the caller. That is not
 * ceremony: it makes the aggregate a pure function of its inputs, so a test
 * asserts on an exact {@code Instant} instead of sleeping, mocking a static, or
 * comparing "roughly now". Deterministic tests are tests people trust.
 *
 * <p>Extends {@link Person} because the exercise requires it; see that class
 * for what the inheritance costs.
 */
public class Customer extends Person {

    private final CustomerId id;
    private HashedPassword password;
    private boolean active;
    private final Instant createdAt;
    private Instant updatedAt;

    /**
     * Events raised by this instance and not yet handed over.
     *
     * <p>The aggregate does not publish anything — it has no way to, and it
     * should not. It <em>records</em> what happened; the use case collects the
     * events and writes them to the outbox inside the same transaction that
     * saves the state change (ADR-0005). Publishing from inside the domain
     * would mean the domain owns a Kafka client, and a rolled-back transaction
     * would leave an event announcing something that never happened.
     */
    private final List<CustomerEvent> pendingEvents = new ArrayList<>();

    private Customer(
            CustomerId id,
            PersonalData profile,
            HashedPassword password,
            boolean active,
            Instant createdAt,
            Instant updatedAt) {
        super(profile);
        this.id = Objects.requireNonNull(id, "customerId must not be null");
        this.password = Objects.requireNonNull(password, "password must not be null");
        this.active = active;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    /**
     * Registers a brand-new customer and records the fact.
     *
     * <p>A named factory rather than a constructor, because "register" and
     * "load an existing row from the database" are different operations with
     * different rules — the first raises an event, the second must not. A single
     * constructor cannot tell them apart, and any code that tries ends up with a
     * boolean flag deciding whether to fire events. That flag is a bug waiting
     * for a rehydration to set it wrong.
     */
    public static Customer register(
            CustomerId id, PersonalData profile, HashedPassword password, boolean active, Instant registeredAt) {
        Customer customer = new Customer(id, profile, password, active, registeredAt, registeredAt);
        customer.pendingEvents.add(new CustomerRegistered(id, profile, active, registeredAt));
        return customer;
    }

    /**
     * Rebuilds a customer from stored state.
     *
     * <p>Raises no events: loading a row is not something that happened to the
     * business. Used exclusively by the persistence adapter.
     */
    public static Customer rehydrate(
            CustomerId id,
            PersonalData profile,
            HashedPassword password,
            boolean active,
            Instant createdAt,
            Instant updatedAt) {
        return new Customer(id, profile, password, active, createdAt, updatedAt);
    }

    /**
     * Updates the profile attributes and records the new full state.
     *
     * <p>Applies to inactive customers as well. Correcting a misspelled address
     * on a deactivated record is legitimate, and blocking it would only push
     * people towards editing the database by hand.
     */
    public void updateProfile(PersonalData newProfile, Instant updatedAt) {
        Objects.requireNonNull(newProfile, "profile must not be null");
        changeProfile(newProfile.name(), newProfile.gender(), newProfile.address(), newProfile.phone());
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        this.pendingEvents.add(new CustomerProfileUpdated(id, personalData(), active, updatedAt));
    }

    /**
     * Replaces the stored credential.
     *
     * <p>Takes an already-hashed value. The domain enforces that a password
     * exists and is not blank; it does not choose the algorithm, because
     * algorithms rotate and that decision belongs behind a port.
     */
    public void changePassword(HashedPassword newPassword, Instant updatedAt) {
        this.password = Objects.requireNonNull(newPassword, "password must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    /**
     * Activates the customer. Idempotent by design — asking for a state that
     * already holds is not an error, because the caller's intent is already
     * satisfied and nothing is hidden by agreeing.
     */
    public void activate(Instant updatedAt) {
        if (active) {
            return;
        }
        this.active = true;
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        this.pendingEvents.add(new CustomerProfileUpdated(id, personalData(), true, updatedAt));
    }

    /**
     * Deactivates the customer.
     *
     * <p>Deliberately <em>not</em> idempotent, unlike {@link #activate}. A
     * second deactivation almost always means the caller is acting on stale
     * state — a lost update, a duplicated request, two operators racing. Failing
     * loudly surfaces that; silently agreeing buries it.
     *
     * @throws CustomerAlreadyInactiveException if the customer is already inactive
     */
    public void deactivate(Instant deactivatedAt) {
        if (!active) {
            throw new CustomerAlreadyInactiveException(id);
        }
        this.active = false;
        this.updatedAt = Objects.requireNonNull(deactivatedAt, "deactivatedAt must not be null");
        this.pendingEvents.add(new CustomerDeactivated(id, deactivatedAt));
    }

    /**
     * Hands over the recorded events and clears them.
     *
     * <p>Draining rather than reading matters: the use case calls this once
     * inside the transaction, and a second call returns nothing. Without that,
     * an aggregate saved twice in one unit of work publishes its events twice,
     * and the consumer's deduplication is left to clean up a mess the producer
     * created.
     */
    public List<CustomerEvent> pullEvents() {
        List<CustomerEvent> drained = List.copyOf(pendingEvents);
        pendingEvents.clear();
        return drained;
    }

    /** Read-only view, for assertions and for the persistence adapter. */
    public List<CustomerEvent> pendingEvents() {
        return Collections.unmodifiableList(pendingEvents);
    }

    public CustomerId id() {
        return id;
    }

    public HashedPassword password() {
        return password;
    }

    public boolean isActive() {
        return active;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    /**
     * Identity is the id, and only the id.
     *
     * <p>This is what makes {@code Customer} an <em>entity</em> rather than a
     * value object: two customers with identical names and addresses are two
     * different people, and the same customer with a changed address is still
     * the same customer. Comparing by attributes — which is what a {@code record}
     * or a Lombok {@code @EqualsAndHashCode} would generate — gets both of those
     * backwards.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Customer that)) {
            return false;
        }
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Customer[id=%s, identification=%s, active=%s]".formatted(id, identification(), active);
    }
}
