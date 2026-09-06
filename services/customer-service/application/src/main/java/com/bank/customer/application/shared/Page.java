package com.bank.customer.application.shared;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * One page of results plus the metadata needed to navigate the rest.
 *
 * @param <T> element type
 */
public record Page<T>(List<T> content, int page, int size, long totalElements) {

    public Page {
        Objects.requireNonNull(content, "content must not be null");
        content = List.copyOf(content);
    }

    public static <T> Page<T> empty(PageRequest request) {
        return new Page<>(List.of(), request.page(), request.size(), 0);
    }

    public int totalPages() {
        return size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
    }

    /**
     * Converts the elements while preserving the pagination metadata.
     *
     * <p>Saves the boundary from rebuilding the envelope by hand every time it
     * maps domain objects to response DTOs — and from getting {@code totalPages}
     * subtly wrong while doing it.
     */
    public <R> Page<R> map(Function<T, R> mapper) {
        return new Page<>(content.stream().map(mapper).toList(), page, size, totalElements);
    }
}
