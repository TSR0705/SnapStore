package com.filex.alert;

/**
 * Incident lifecycle status.
 *
 * <p>Status transitions:
 *
 * <ul>
 *   <li>OPEN → INVESTIGATING
 *   <li>INVESTIGATING → RESOLVED
 *   <li>INVESTIGATING → DISMISSED
 *   <li>OPEN → DISMISSED
 *   <li>RESOLVED → OPEN (reopened)
 *   <li>DISMISSED → OPEN (reopened)
 * </ul>
 */
public enum IncidentStatus {
  /** Incident is open and awaiting investigation. */
  OPEN,

  /** Incident is actively being investigated. */
  INVESTIGATING,

  /** Incident has been resolved. */
  RESOLVED,

  /** Incident has been dismissed as false positive or non-issue. */
  DISMISSED
}
