package com.filex.persistence;

/**
 * Thrown when a database migration fails.
 *
 * <p>This is a fatal error — the application cannot continue with an incomplete or corrupted
 * schema.
 */
public final class MigrationException extends RuntimeException {

  public MigrationException(String message) {
    super(message);
  }

  public MigrationException(String message, Throwable cause) {
    super(message, cause);
  }
}
