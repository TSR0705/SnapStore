package com.filex.config;

/**
 * Thrown when the application configuration cannot be resolved or
 * required directories cannot be created during bootstrap.
 *
 * <p>This is a fatal, non-recoverable condition — the application
 * must not continue if configuration fails.
 */
public final class ConfigurationException extends RuntimeException {

    public ConfigurationException(String message) {
        super(message);
    }

    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
