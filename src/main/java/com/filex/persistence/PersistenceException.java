package com.filex.persistence;

/**
 * Exception thrown when persistence operations fail.
 *
 * <p>Wraps underlying SQL exceptions and provides context about what persistence operation failed.
 */
public final class PersistenceException extends Exception {

  public PersistenceException(String message) {
    super(message);
  }

  public PersistenceException(String message, Throwable cause) {
    super(message, cause);
  }
}
