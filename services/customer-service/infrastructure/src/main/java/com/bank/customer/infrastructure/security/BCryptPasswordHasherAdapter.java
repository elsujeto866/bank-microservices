package com.bank.customer.infrastructure.security;

import com.bank.customer.application.port.out.PasswordHasher;
import com.bank.customer.domain.model.HashedPassword;
import com.bank.customer.domain.model.PlainPassword;
import org.springframework.security.crypto.password.PasswordEncoder;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

/**
 * BCrypt implementation of the password hashing port.
 *
 * <p>The scheduler is the interesting part, and it is <strong>not</strong> the
 * one the database uses.
 *
 * <p>Blocking on a socket and burning CPU are different problems. A thread
 * waiting on JDBC is asleep, so a large pool of them is fine — that is what
 * {@code boundedElastic} is for. A thread computing a BCrypt hash is pegging a
 * core, and a hundred of those on eight cores do not run a hundred times
 * faster; they run at the same total speed with a hundred times the context
 * switching. {@code parallel} is sized to the core count for exactly this.
 *
 * <p>Putting CPU work on {@code boundedElastic} is a common and expensive
 * mistake: under load it grows to its cap, every thread fights for the same
 * cores, and latency collapses for reasons that look nothing like their cause.
 */
public class BCryptPasswordHasherAdapter implements PasswordHasher {

    private final PasswordEncoder passwordEncoder;
    private final Scheduler cpuScheduler;

    public BCryptPasswordHasherAdapter(PasswordEncoder passwordEncoder, Scheduler cpuScheduler) {
        this.passwordEncoder = passwordEncoder;
        this.cpuScheduler = cpuScheduler;
    }

    @Override
    public Mono<HashedPassword> hash(PlainPassword plainPassword) {
        return Mono.fromCallable(() -> new HashedPassword(passwordEncoder.encode(plainPassword.value())))
                .subscribeOn(cpuScheduler);
    }
}
