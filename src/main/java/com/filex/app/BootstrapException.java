package com.filex.app;

/**
 * Thrown when the application bootstrap sequence fails.
 *
 * <p>This is a fatal, non-recoverable error. The application must terminate if bootstrap fails —
 * there is no valid fallback state.
 */
public final class BootstrapException extends RuntimeException {

  public BootstrapException(String message) {
    super(message);
  }

  public BootstrapException(String message, Throwable cause) {
    super(message, cause);
  }
}
