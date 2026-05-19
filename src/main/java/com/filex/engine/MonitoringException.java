package com.filex.engine;

/** Exception thrown when monitoring engine operations fail. */
public class MonitoringException extends Exception {

  public MonitoringException(String message) {
    super(message);
  }

  public MonitoringException(String message, Throwable cause) {
    super(message, cause);
  }
}
