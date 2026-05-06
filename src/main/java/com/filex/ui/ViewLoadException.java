package com.filex.ui;

/**
 * Thrown when an FXML view cannot be loaded or parsed.
 *
 * <p>This is typically a fatal error indicating a missing resource,
 * malformed FXML, or controller instantiation failure.
 */
public final class ViewLoadException extends RuntimeException {

    public ViewLoadException(String message) {
        super(message);
    }

    public ViewLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
