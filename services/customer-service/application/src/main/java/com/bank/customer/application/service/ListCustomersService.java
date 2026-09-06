package com.bank.customer.application.service;

import com.bank.customer.application.port.in.ListCustomers;
import com.bank.customer.application.port.out.CustomerRepository;
import com.bank.customer.application.query.CustomerFilter;
import com.bank.customer.application.shared.Page;
import com.bank.customer.application.shared.PageRequest;
import com.bank.customer.domain.model.Customer;
import java.util.Objects;
import reactor.core.publisher.Mono;

/**
 * Lists customers.
 *
 * <p>Deliberately trivial. A read with no business rule has nothing to
 * orchestrate, and inventing work for it would only add a layer to step through
 * while debugging.
 *
 * <p>It still exists rather than letting the controller call the repository
 * directly: that shortcut would put an infrastructure-to-infrastructure
 * dependency across the hexagon, and the day this query grows a rule — hide
 * inactive customers from a given caller, say — there would be no place to put
 * it except the controller.
 */
public class ListCustomersService implements ListCustomers {

    private final CustomerRepository customers;

    public ListCustomersService(CustomerRepository customers) {
        this.customers = Objects.requireNonNull(customers, "customers must not be null");
    }

    @Override
    public Mono<Page<Customer>> list(CustomerFilter filter, PageRequest pageRequest) {
        Objects.requireNonNull(filter, "filter must not be null");
        Objects.requireNonNull(pageRequest, "pageRequest must not be null");

        return customers.findAll(filter, pageRequest);
    }
}
