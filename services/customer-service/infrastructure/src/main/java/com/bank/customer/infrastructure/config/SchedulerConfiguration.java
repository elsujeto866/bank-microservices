package com.bank.customer.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * The schedulers that keep blocking work off the event loop — ADR-0003, made
 * concrete.
 */
@Configuration
public class SchedulerConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SchedulerConfiguration.class);

    /**
     * Queued tasks per thread before {@code RejectedExecutionException}.
     *
     * <p>Bounded on purpose. An unbounded queue does not prevent overload, it
     * hides it: requests pile up, latency climbs past every client's timeout,
     * and the service keeps accepting work nobody is still waiting for. Failing
     * fast turns a silent death spiral into an alert.
     */
    private static final int QUEUE_CAPACITY = 10_000;

    /**
     * The scheduler every JDBC call runs on.
     *
     * <p><strong>Its size is read from the HikariCP pool size, and that is the
     * whole point of this bean.</strong> ADR-0003 says the two must be sized
     * together; wiring them to the same property makes it impossible for them
     * to drift.
     *
     * <p>Why they must match: a thread here can only do useful work while
     * holding a database connection. Give this pool 80 threads and Hikari 10,
     * and 70 threads spend their lives blocked waiting for a <em>connection</em>
     * rather than for a query — the bottleneck moved, it did not shrink, and
     * the thread dump now blames the wrong thing. One thread per connection is
     * the only ratio that cannot queue on itself.
     *
     * <p>Note this is a dedicated scheduler, not the shared
     * {@code Schedulers.boundedElastic()}. The shared one defaults to
     * {@code 10 × cores} and is used by anything in the process that asks for
     * it; a private, correctly sized pool means database work cannot be starved
     * by an unrelated library that decided to offload something.
     */
    @Bean(destroyMethod = "dispose")
    public Scheduler jdbcScheduler(@Value("${spring.datasource.hikari.maximum-pool-size}") int connectionPoolSize) {
        log.info(
                "Creating jdbcScheduler with {} threads, matched to the HikariCP pool size (ADR-0003)",
                connectionPoolSize);
        return Schedulers.newBoundedElastic(connectionPoolSize, QUEUE_CAPACITY, "jdbc");
    }

    /**
     * The scheduler for CPU-bound work — currently only password hashing.
     *
     * <p>{@code Schedulers.parallel()} is sized to the available cores, which is
     * correct for work that never blocks. Running BCrypt on
     * {@code jdbcScheduler} instead would let hashing consume the very threads
     * the database needs, and would size a CPU pool by a connection count.
     */
    @Bean
    public Scheduler cpuScheduler() {
        return Schedulers.parallel();
    }
}
