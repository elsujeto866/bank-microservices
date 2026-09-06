package com.bank.customer.domain.model;

import java.util.Objects;

/**
 * A human being, with the attributes the exercise specifies: name, gender,
 * identification, address and telephone.
 *
 * <p>The exercise requires {@code Customer} to inherit from this class, and it
 * does. Worth knowing what that costs, because it is the kind of thing an
 * interviewer asks about:
 *
 * <ul>
 *   <li>Inheritance is the tightest coupling a language offers. Every change
 *       here reaches every subclass, and a subclass cannot opt out.
 *   <li>A {@code Customer} arguably <em>has</em> personal data rather than
 *       <em>being</em> a person — composition would model it as a field, and
 *       would let an employee and a customer share the data without sharing a
 *       type hierarchy.
 *   <li>It leaks into persistence: JPA must be told how to map the hierarchy
 *       (single table, joined, or table-per-class), and that choice has real
 *       query cost. It is decided in the infrastructure layer, not here.
 * </ul>
 *
 * <p>The hierarchy is one level deep and the exercise asks for it, so the cost
 * is contained. The reasoning is written down rather than assumed.
 *
 * <p>Mutators are {@code protected}: only a subclass may change this state, and
 * only through an intention-revealing method of its own. There are no public
 * setters, because {@code customer.setAddress(x)} says nothing about
 * <em>why</em> the address changed, and a domain model that cannot express why
 * is a database row with extra steps.
 */
public abstract class Person {

    private PersonName name;
    private Gender gender;
    private final Identification identification;
    private Address address;
    private PhoneNumber phone;

    protected Person(PersonalData data) {
        Objects.requireNonNull(data, "personal data must not be null");
        this.name = data.name();
        this.gender = data.gender();
        this.identification = data.identification();
        this.address = data.address();
        this.phone = data.phone();
    }

    /**
     * Replaces the mutable profile attributes.
     *
     * <p>{@code identification} is absent by design: it is the natural key and
     * the target of a uniqueness constraint. Letting it change through a
     * routine profile edit turns "update my phone number" into an identity
     * change, which is a different operation with different rules and a
     * different audit trail.
     */
    protected void changeProfile(PersonName newName, Gender newGender, Address newAddress, PhoneNumber newPhone) {
        this.name = Objects.requireNonNull(newName, "name must not be null");
        this.gender = Objects.requireNonNull(newGender, "gender must not be null");
        this.address = Objects.requireNonNull(newAddress, "address must not be null");
        this.phone = Objects.requireNonNull(newPhone, "phone must not be null");
    }

    public PersonName name() {
        return name;
    }

    public Gender gender() {
        return gender;
    }

    public Identification identification() {
        return identification;
    }

    public Address address() {
        return address;
    }

    public PhoneNumber phone() {
        return phone;
    }

    public PersonalData personalData() {
        return new PersonalData(name, gender, identification, address, phone);
    }
}
