package com.bank.customer.infrastructure;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point.
 *
 * <p>Note where it lives: in the <em>infrastructure</em> module. The Spring Boot
 * application class is a framework artifact, so it belongs in the ring where
 * frameworks are allowed. The domain and application modules do not depend on
 * it and could be published as libraries with no main class at all.
 */
@SpringBootApplication
public class CustomerServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CustomerServiceApplication.class, args);
    }
}
