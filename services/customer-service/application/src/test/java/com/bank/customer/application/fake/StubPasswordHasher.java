package com.bank.customer.application.fake;

import com.bank.customer.application.port.out.PasswordHasher;
import com.bank.customer.domain.model.HashedPassword;
import com.bank.customer.domain.model.PlainPassword;
import reactor.core.publisher.Mono;

/**
 * A deterministic, instant password hasher for tests.
 *
 * <p>Prefixing the plaintext is not a hash, and that is the point: a test needs
 * to see that hashing happened and that the right value went in. Running real
 * BCrypt here would add roughly 100 ms to every test that registers a customer
 * and prove nothing extra — the correctness of BCrypt is BCrypt's problem, and
 * the adapter that wires it is verified in an integration test.
 */
public class StubPasswordHasher implements PasswordHasher {

    public static final String PREFIX = "hashed:";

    @Override
    public Mono<HashedPassword> hash(PlainPassword plainPassword) {
        return Mono.just(new HashedPassword(PREFIX + plainPassword.value()));
    }
}
