package com.bank.customer.application.port.in;

import com.bank.customer.application.command.PatchCustomerCommand;
import com.bank.customer.domain.model.Customer;
import reactor.core.publisher.Mono;

/** Inbound port: change some of a customer's attributes (HTTP PATCH). */
public interface PatchCustomer {

    Mono<Customer> patch(PatchCustomerCommand command);
}
