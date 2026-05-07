package com.filex.alert;

/**
 * Incident lifecycle status.
 *
 * <p>Status transitions:
 * <ul>
 *   <li>OPEN → INVESTIGATING</li>
 *   <li>INVESTIGATING → RESOLVED</li>
 *   <li>INVESTIGATING → DISMISSED</li>
 *   <li>OPEN → DISMISSED</li>
 *   <li>RESOLVED → OPEN (reopened)</li>
 *   <li>DISMISSED → OPEN (reopened)</li>
 * </ul>
 */
public enum IncidentStatus {
    /**
     * Incident is open and awaiting investigation.
     */
    OPEN,

    /**
     * Incident is actively being investigated.
     */
    INVESTIGATING,

    /**
     * Incident has been resolved.
     */
    RESOLVED,

    /**
     * Incident has been dismissed as false positive or non-issue.
     */
    DISMISSED
}
