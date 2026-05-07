package com.filex.detection;

/**
 * Interface for detection rules that evaluate monitoring events
 * for suspicious activity.
 *
 * <p>Each rule is:
 * <ul>
 *   <li>Modular and independent</li>
 *   <li>Testable in isolation</li>
 *   <li>Stateless (no shared mutable state)</li>
 *   <li>Thread-safe</li>
 * </ul>
 *
 * <p>Rules receive a {@link DetectionContext} containing the current
 * event and recent event history for temporal correlation.
 */
public interface DetectionRule {

    /**
     * Returns the unique name of this detection rule.
     */
    String name();

    /**
     * Returns a human-readable description of what this rule detects.
     */
    String description();

    /**
     * Evaluates the current monitoring event for suspicious activity.
     *
     * @param context the detection context containing current event and history
     * @return detection result indicating whether suspicious activity was detected
     */
    DetectionResult evaluate(DetectionContext context);

    /**
     * Returns whether this rule is currently enabled.
     * Disabled rules are not evaluated.
     */
    default boolean isEnabled() {
        return true;
    }

    /**
     * Returns the priority of this rule for future ordering support.
     * Higher priority rules may be evaluated first in future implementations.
     * Default priority is 0 (normal).
     */
    default int priority() {
        return 0;
    }
}
