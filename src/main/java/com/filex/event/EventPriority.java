package com.filex.event;

/**
 * Event priority levels for future priority-based dispatch.
 *
 * <p>This enum provides a foundation for future event prioritization
 * without implementing full priority queue complexity yet.
 *
 * <p>Priority semantics:
 * <ul>
 *   <li>HIGH — critical system events (shutdown, errors, alerts)</li>
 *   <li>NORMAL — standard application events (default)</li>
 *   <li>LOW — background/housekeeping events (metrics, cleanup)</li>
 * </ul>
 *
 * <p>Current implementation treats all priorities equally.
 * Future phases may introduce priority-aware dispatch scheduling.
 */
public enum EventPriority {

    /**
     * High-priority events requiring immediate attention.
     * Examples: system shutdown, critical alerts, error conditions.
     */
    HIGH,

    /**
     * Normal-priority events (default).
     * Examples: file events, configuration changes, user actions.
     */
    NORMAL,

    /**
     * Low-priority events for background processing.
     * Examples: metrics collection, periodic cleanup, statistics.
     */
    LOW
}
