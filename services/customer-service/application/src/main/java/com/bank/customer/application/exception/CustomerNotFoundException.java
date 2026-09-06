package com.bank.customer.application.exception;

import com.bank.customer.domain.model.CustomerId;
import com.bank.customer.domain.model.Identification;

/** No customer exists for the given identifier. Maps to HTTP 404 at the boundary. */
public class CustomerNotFoundException extends ApplicationException {

    private static final String ERROR_CODE = "customer-not-found";

    public CustomerNotFoundException(CustomerId customerId) {
        super(ERROR_CODE, "No customer exists with id %s".formatted(customerId));
    }

    public CustomerNotFoundException(Identification identification) {
        super(ERROR_CODE, "No customer exists with identification %s".formatted(identification));
    }
}
