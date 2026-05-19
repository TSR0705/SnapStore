package com.filex.alert;

/** Exception thrown when alert processing fails. */
public class AlertException extends Exception {

  public AlertException(String message) {
    super(message);
  }

  public AlertException(String message, Throwable cause) {
    super(message, cause);
  }
}
