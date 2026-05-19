package com.filex.alert;

/**
 * Incident severity levels.
 *
 * <p>Severity can escalate based on:
 *
 * <ul>
 *   <li>Multiple related detections
 *   <li>Repeated occurrences
 *   <li>Correlation patterns
 * </ul>
 */
public enum IncidentSeverity {
  /** Low severity - informational or minor suspicious activity. */
  LOW(1),

  /** Medium severity - suspicious activity requiring attention. */
  MEDIUM(2),

  /** High severity - likely malicious activity requiring immediate attention. */
  HIGH(3),

  /** Critical severity - confirmed malicious activity requiring urgent response. */
  CRITICAL(4);

  private final int level;

  IncidentSeverity(int level) {
    this.level = level;
  }

  /** Returns the numeric severity level for comparison. */
  public int getLevel() {
    return level;
  }

  /** Returns the higher of two severities. */
  public static IncidentSeverity max(IncidentSeverity a, IncidentSeverity b) {
    return a.level >= b.level ? a : b;
  }

  /** Escalates severity by one level (capped at CRITICAL). */
  public IncidentSeverity escalate() {
    return switch (this) {
      case LOW -> MEDIUM;
      case MEDIUM -> HIGH;
      case HIGH, CRITICAL -> CRITICAL;
    };
  }
}
