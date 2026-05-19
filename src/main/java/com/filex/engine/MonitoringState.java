package com.filex.engine;

/**
 * Lifecycle states for the monitoring engine.
 *
 * <p>State transitions:
 *
 * <pre>
 * IDLE → STARTING → RUNNING → STOPPING → STOPPED
 *   ↓       ↓          ↓          ↓
 *   └───────┴──────────┴──────────→ FAILED
 * </pre>
 */
public enum MonitoringState {

  /** Initial state before monitoring has started. */
  IDLE,

  /** Monitoring is starting (registering watches). */
  STARTING,

  /** Monitoring is actively running. */
  RUNNING,

  /** Monitoring is shutting down. */
  STOPPING,

  /** Monitoring has stopped cleanly. */
  STOPPED,

  /** Monitoring encountered a fatal error. */
  FAILED
}
