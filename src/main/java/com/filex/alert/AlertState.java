package com.filex.alert;

/** AlertEngine runtime state. */
public enum AlertState {
  /** AlertEngine has not been started. */
  IDLE,

  /** AlertEngine is starting up. */
  STARTING,

  /** AlertEngine is running and processing alerts. */
  RUNNING,

  /** AlertEngine is shutting down. */
  STOPPING,

  /** AlertEngine has stopped. */
  STOPPED,

  /** AlertEngine encountered a fatal error. */
  FAILED
}
