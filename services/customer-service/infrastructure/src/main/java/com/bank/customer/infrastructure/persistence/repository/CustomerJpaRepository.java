package com.bank.customer.infrastructure.persistence.repository;

import com.bank.customer.infrastructure.persistence.entity.CustomerEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JPA repository. Blocking, by nature.
 *
 * <p>This interface is an implementation detail of the adapter that wraps it.
 * Nothing outside this package refers to it: the application layer knows only
 * {@code CustomerRepository}, the port it owns. If it were exposed, Spring
 * Data's {@code Page}, {@code Pageable} and derived-query naming would become
 * the application's vocabulary — and that is the coupling the hexagon exists to
 * prevent.
 */
public interface CustomerJpaRepository extends JpaRepository<CustomerEntity, UUID> {

    Optional<CustomerEntity> findByIdentification(String identification);

    boolean existsByIdentification(String identification);

    /**
     * One query for every combination of the two optional filters.
     *
     * <p>Each parameter is skipped when null, so a single prepared statement
     * serves all four cases. The alternative — four derived query methods and a
     * chain of ifs choosing between them — is four statements to keep in sync
     * and four plans in the cache.
     *
     * <p>Explicit ORDER BY, because PostgreSQL makes no promise about row order
     * without one. Paginate an unordered query and the same row can appear on
     * page one and page two while another never appears at all.
     */
    @Query("""
            SELECT c FROM CustomerEntity c
            WHERE (:identification IS NULL OR c.identification = :identification)
              AND (:active IS NULL OR c.active = :active)
            ORDER BY c.createdAt ASC, c.id ASC
            """)
    Page<CustomerEntity> search(
            @Param("identification") String identification, @Param("active") Boolean active, Pageable pageable);
}
