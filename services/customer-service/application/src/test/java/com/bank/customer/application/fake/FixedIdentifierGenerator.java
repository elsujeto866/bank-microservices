package com.bank.customer.application.fake;

import com.bank.customer.application.port.out.IdentifierGenerator;
import com.bank.customer.domain.model.CustomerId;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

/**
 * Hands out identifiers in a predetermined order.
 *
 * <p>This is the payoff of injecting the generator instead of calling
 * {@code UUID.randomUUID()} inside the use case: a test can assert the exact id
 * that was returned and saved, rather than settling for "some id came back".
 */
public class FixedIdentifierGenerator implements IdentifierGenerator {

    private final Deque<CustomerId> queue;

    public FixedIdentifierGenerator(CustomerId... ids) {
        this.queue = new ArrayDeque<>(Arrays.asList(ids));
    }

    @Override
    public CustomerId newCustomerId() {
        if (queue.isEmpty()) {
            throw new IllegalStateException("FixedIdentifierGenerator ran out of prepared identifiers");
        }
        return queue.poll();
    }
}
