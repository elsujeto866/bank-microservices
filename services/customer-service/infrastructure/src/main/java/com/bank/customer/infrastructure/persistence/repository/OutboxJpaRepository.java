package com.bank.customer.infrastructure.persistence.repository;

import com.bank.customer.infrastructure.persistence.entity.OutboxEventEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for the outbox table.
 *
 * <p>Only writes for now. The relay that reads pending rows and publishes them
 * to Kafka arrives in a later stage — the guarantee this table provides is
 * about the write being atomic with the state change, and that guarantee is
 * already in force.
 */
public interface OutboxJpaRepository extends JpaRepository<OutboxEventEntity, UUID> {}
