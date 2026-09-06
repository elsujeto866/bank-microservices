package com.bank.customer.application.port.in;

import com.bank.customer.application.query.CustomerFilter;
import com.bank.customer.application.shared.Page;
import com.bank.customer.application.shared.PageRequest;
import com.bank.customer.domain.model.Customer;
import reactor.core.publisher.Mono;

/**
 * Inbound port: list customers, filtered and paginated.
 *
 * <p>Returns {@code Mono<Page<Customer>>} rather than {@code Flux<Customer>}.
 * A {@code Flux} would stream rows without ever knowing the total count, and
 * the contract's page envelope requires {@code totalElements}. Streaming is the
 * right shape for the statement report; it is the wrong shape for a paged list.
 */
public interface ListCustomers {

    Mono<Page<Customer>> list(CustomerFilter filter, PageRequest pageRequest);
}
