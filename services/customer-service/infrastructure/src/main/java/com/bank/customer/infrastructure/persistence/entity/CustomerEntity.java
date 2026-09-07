package com.bank.customer.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * How a customer is stored. <strong>Not</strong> how a customer is modelled.
 *
 * <p>This is the single most useful thing to notice in the persistence layer:
 * the domain has {@code Customer extends Person}, and this table has no
 * hierarchy at all. One flat table, no {@code @Inheritance}, no discriminator,
 * no join.
 *
 * <p>That is allowed precisely because the entity is a separate class. Mapping
 * the domain hierarchy directly would have forced a choice between
 * {@code SINGLE_TABLE} (a wide table full of nullable columns),
 * {@code JOINED} (an extra join on every single read) and
 * {@code TABLE_PER_CLASS} (unions on polymorphic queries) — a real query cost
 * paid forever, to satisfy a modelling decision the database does not care
 * about. Keeping the two models separate costs one mapper class and buys the
 * freedom to shape each one for its own job.
 *
 * <p>Lombok earns its place here, unlike in the domain. JPA requires a no-args
 * constructor and mutable fields; there is no behaviour to protect, so
 * generated accessors are exactly right. The domain model is hand-written
 * because every method there is a business decision.
 */
@Entity
@Table(name = "customers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CustomerEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    /**
     * {@code EnumType.STRING}, never {@code ORDINAL}.
     *
     * <p>{@code ORDINAL} stores the enum's position, so inserting a new constant
     * in the middle of the declaration silently rewrites the meaning of every
     * row already in the table. Nothing fails; the data is simply wrong from
     * then on. The extra bytes for a string are the cheapest insurance in this
     * file.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "gender", nullable = false, length = 20)
    private GenderCode gender;

    @Column(name = "identification", nullable = false, unique = true, length = 20)
    private String identification;

    @Column(name = "address", nullable = false, length = 200)
    private String address;

    @Column(name = "phone", nullable = false, length = 20)
    private String phone;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Optimistic lock.
     *
     * <p>Hibernate adds {@code AND version = ?} to every update and bumps the
     * value. If another transaction got there first, zero rows match and
     * Hibernate raises {@code OptimisticLockingFailureException} — which the
     * adapter translates into {@code ConcurrentModificationConflictException}.
     *
     * <p>This one column is what lets the use cases avoid holding a transaction
     * open across a read-modify-write. No lock is taken, both writers proceed,
     * and the loser is rejected at commit instead of waiting.
     */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    /** The persisted spelling of the domain's {@code Gender}. */
    public enum GenderCode {
        MALE,
        FEMALE,
        OTHER,
        UNSPECIFIED
    }
}
