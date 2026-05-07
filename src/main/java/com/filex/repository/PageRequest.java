package com.filex.repository;

/**
 * Pagination request parameters.
 *
 * <p>Immutable value object for specifying page number and size.
 * Used by repositories to implement LIMIT/OFFSET queries.
 */
public record PageRequest(int page, int size) {

    public PageRequest {
        if (page < 0) throw new IllegalArgumentException("page must be >= 0");
        if (size < 1) throw new IllegalArgumentException("size must be >= 1");
        if (size > 1000) throw new IllegalArgumentException("size must be <= 1000");
    }

    /**
     * Returns the SQL LIMIT value.
     */
    public int limit() {
        return size;
    }

    /**
     * Returns the SQL OFFSET value.
     */
    public int offset() {
        return page * size;
    }

    public static PageRequest of(int page, int size) {
        return new PageRequest(page, size);
    }

    public static PageRequest first(int size) {
        return new PageRequest(0, size);
    }
}
