package com.bank.customer.infrastructure.config;

import com.bank.customer.application.port.out.CustomerRepository;
import com.bank.customer.application.port.out.IdentifierGenerator;
import com.bank.customer.application.port.out.PasswordHasher;
import com.bank.customer.application.service.DeactivateCustomerService;
import com.bank.customer.application.service.GetCustomerService;
import com.bank.customer.application.service.ListCustomersService;
import com.bank.customer.application.service.PatchCustomerService;
import com.bank.customer.application.service.RegisterCustomerService;
import com.bank.customer.application.service.ReplaceCustomerService;
import com.bank.customer.infrastructure.identity.UuidIdentifierGenerator;
import com.bank.customer.infrastructure.persistence.CustomerPersistenceGateway;
import com.bank.customer.infrastructure.persistence.CustomerRepositoryAdapter;
import com.bank.customer.infrastructure.security.BCryptPasswordHasherAdapter;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import reactor.core.scheduler.Scheduler;

/**
 * The composition root.
 *
 * <p><strong>This file is the only place in the service that knows a dependency
 * injection container exists.</strong> Not one class in the domain or the
 * application layer carries {@code @Service}, {@code @Component} or
 * {@code @Autowired}; they are plain Java objects with constructors, and this
 * class calls those constructors.
 *
 * <p>That is what makes the claim in ADR-0002 true rather than aspirational.
 * Replace Spring with Micronaut, or wire the whole graph by hand in a
 * {@code main} method, and the business code is untouched — only this file
 * changes.
 *
 * <p>The cost is honest: every new use case needs a line here. That is a real
 * chore, and component scanning would remove it. It would also put a framework
 * annotation on every use case, which is precisely the coupling being avoided.
 * Explicit wiring also makes the dependency graph readable in one screen
 * instead of inferred from annotations scattered across thirty files.
 */
@Configuration
public class UseCaseConfiguration {

    /**
     * A {@link Clock} bean rather than {@code Instant.now()} scattered through
     * the code.
     *
     * <p>Injecting time is what lets every use case be tested against an exact
     * timestamp. Tests replace this with {@code Clock.fixed(...)} and assert on
     * the value instead of on "roughly now".
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * BCrypt with an explicit strength.
     *
     * <p>The cost factor is the security parameter: each increment doubles the
     * work an attacker must do per guess, and doubles ours per login. 12 is a
     * deliberate choice, not the default (10) accepted by omission — and it is
     * written here so it can be revisited as hardware gets faster.
     *
     * <p>BCrypt salts every hash internally, so identical passwords produce
     * different hashes and a stolen table cannot be attacked with one rainbow
     * table.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public PasswordHasher passwordHasher(PasswordEncoder passwordEncoder, Scheduler cpuScheduler) {
        return new BCryptPasswordHasherAdapter(passwordEncoder, cpuScheduler);
    }

    @Bean
    public IdentifierGenerator identifierGenerator() {
        return new UuidIdentifierGenerator();
    }

    @Bean
    public CustomerRepository customerRepository(CustomerPersistenceGateway gateway, Scheduler jdbcScheduler) {
        return new CustomerRepositoryAdapter(gateway, jdbcScheduler);
    }

    @Bean
    public RegisterCustomerService registerCustomerService(
            CustomerRepository customers,
            PasswordHasher passwordHasher,
            IdentifierGenerator identifiers,
            Clock clock) {
        return new RegisterCustomerService(customers, passwordHasher, identifiers, clock);
    }

    @Bean
    public ReplaceCustomerService replaceCustomerService(
            CustomerRepository customers, PasswordHasher passwordHasher, Clock clock) {
        return new ReplaceCustomerService(customers, passwordHasher, clock);
    }

    @Bean
    public PatchCustomerService patchCustomerService(
            CustomerRepository customers, PasswordHasher passwordHasher, Clock clock) {
        return new PatchCustomerService(customers, passwordHasher, clock);
    }

    @Bean
    public DeactivateCustomerService deactivateCustomerService(CustomerRepository customers, Clock clock) {
        return new DeactivateCustomerService(customers, clock);
    }

    @Bean
    public GetCustomerService getCustomerService(CustomerRepository customers) {
        return new GetCustomerService(customers);
    }

    @Bean
    public ListCustomersService listCustomersService(CustomerRepository customers) {
        return new ListCustomersService(customers);
    }
}
