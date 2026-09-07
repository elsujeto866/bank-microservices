package com.bank.customer.infrastructure.identity;

import com.bank.customer.application.port.out.IdentifierGenerator;
import com.bank.customer.domain.model.CustomerId;
import java.util.UUID;

/**
 * Mints identifiers with {@link UUID#randomUUID()} (a version 4, random UUID).
 *
 * <p>Worth knowing what this costs, because it is a real index consideration.
 * Random UUIDs are unordered, so every insert lands at a random position in the
 * primary-key B-tree. At scale that spreads writes across the whole index
 * instead of appending to its right edge, which fragments pages and hurts cache
 * locality. A time-ordered UUID (v7) inserts sequentially and avoids it.
 *
 * <p>Version 4 is kept here deliberately: the volumes in this system are
 * nowhere near where that matters, and a random id leaks nothing about creation
 * time. Should it ever matter, this class is the only file that changes —
 * which is the argument for the port being here at all.
 */
public class UuidIdentifierGenerator implements IdentifierGenerator {

    @Override
    public CustomerId newCustomerId() {
        return new CustomerId(UUID.randomUUID());
    }
}
