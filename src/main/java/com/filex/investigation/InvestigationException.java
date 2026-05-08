package com.filex.investigation;

/**
 * Exception thrown when investigation queries fail.
 *
 * <p>Wraps underlying SQL exceptions and provides investigation-specific
 * error context. Investigation failures must NEVER mutate forensic state.
 */
public final class InvestigationException extends Exception {

    public InvestigationException(String message) {
        super(message);
    }

    public InvestigationException(String message, Throwable cause) {
        super(message, cause);
    }

    public InvestigationException(Throwable cause) {
        super(cause);
    }
}
