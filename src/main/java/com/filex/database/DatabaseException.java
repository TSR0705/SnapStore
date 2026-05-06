package com.filex.database;

/**
 * Thrown when a database operation fails in a non-recoverable way.
 *
 * <p>Wraps {@link java.sql.SQLException} and other low-level database
 * errors into a domain-level exception that callers can handle without
 * depending on JDBC types.
 */
public final class DatabaseException extends RuntimeException {

    public DatabaseException(String message) {
        super(message);
    }

    public DatabaseException(String message, Throwable cause) {
        super(message, cause);
    }
}
