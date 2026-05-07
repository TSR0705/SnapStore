package com.filex.repository;

import java.util.List;

/**
 * A page of query results.
 *
 * @param <T> the entity type
 */
public record Page<T>(
        List<T> content,
        int page,
        int size,
        long totalElements
) {
    public int totalPages() {
        return size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
    }

    public boolean hasNext() {
        return page < totalPages() - 1;
    }

    public boolean hasPrevious() {
        return page > 0;
    }

    public boolean isEmpty() {
        return content.isEmpty();
    }
}
