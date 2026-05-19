package com.filex.workspace;

/**
 * Exception thrown when workspace operations fail.
 *
 * <p>Indicates failures in:
 *
 * <ul>
 *   <li>Workspace state transitions
 *   <li>Session persistence/restoration
 *   <li>Navigation operations
 *   <li>State synchronization
 * </ul>
 *
 * <p>This is a checked exception to force explicit error handling in workspace operations.
 */
public class WorkspaceException extends Exception {

  public WorkspaceException(String message) {
    super(message);
  }

  public WorkspaceException(String message, Throwable cause) {
    super(message, cause);
  }
}
