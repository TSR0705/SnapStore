package com.filex.detection;

/**
 * Severity levels for detected suspicious activities.
 *
 * <p>Severity indicates the potential impact or risk level of a detected behavior.
 */
public enum Severity {

  /**
   * Low severity - minor suspicious behavior. Examples: single hidden file creation, isolated temp
   * file activity.
   */
  LOW,

  /**
   * Medium severity - moderately suspicious behavior. Examples: rapid file modifications,
   * suspicious extension changes.
   */
  MEDIUM,

  /**
   * High severity - highly suspicious behavior. Examples: mass deletions, sensitive directory
   * access patterns.
   */
  HIGH,

  /**
   * Critical severity - extremely suspicious behavior requiring immediate attention. Examples:
   * ransomware-like patterns, system file tampering.
   */
  CRITICAL
}
