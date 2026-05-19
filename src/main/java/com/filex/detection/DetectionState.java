package com.filex.detection;

/**
 * Lifecycle states for the detection engine.
 *
 * <p>State transitions:
 *
 * <pre>
 * IDLE → STARTING → RUNNING → STOPPING → STOPPED
 *   ↓       ↓          ↓          ↓
 *   └───────┴──────────┴──────────→ FAILED
 * </pre>
 */
public enum DetectionState {

  /** Initial state before detection has started. */
  IDLE,

  /** Detection is starting (registering rules, subscribing to events). */
  STARTING,

  /** Detection is actively running. */
  RUNNING,

  /** Detection is shutting down. */
  STOPPING,

  /** Detection has stopped cleanly. */
  STOPPED,

  /** Detection encountered a fatal error. */
  FAILED
}
