package com.bank.customer.application.port.in;

import com.bank.customer.application.command.ReplaceCustomerCommand;
import com.bank.customer.domain.model.Customer;
import reactor.core.publisher.Mono;

/** Inbound port: replace a customer's profile wholesale (HTTP PUT). */
public interface ReplaceCustomer {

    Mono<Customer> replace(ReplaceCustomerCommand command);
}
