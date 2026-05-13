package com.filex.workspace;

/**
 * Enumeration of investigation workspace navigation contexts.
 *
 * <p>Represents the current focus area of the operator's investigation.
 * Used for:
 * <ul>
 *   <li>Context-aware navigation</li>
 *   <li>Panel visibility control</li>
 *   <li>Workflow state tracking</li>
 *   <li>Session restoration</li>
 * </ul>
 *
 * <p>Navigation contexts are hierarchical:
 * <pre>
 * INCIDENT_LIST
 *   → INCIDENT_DETAIL
 *       → EVIDENCE_EXPLORATION
 *       → TIMELINE_REPLAY
 *       → CORRELATION_ANALYSIS
 * </pre>
 */
public enum NavigationContext {
    
    /**
     * Operator is viewing the incident list (triage view).
     * No specific incident selected.
     */
    INCIDENT_LIST,
    
    /**
     * Operator is viewing a specific incident's details.
     * Incident ID is set in workspace state.
     */
    INCIDENT_DETAIL,
    
    /**
     * Operator is exploring evidence chain for an incident.
     * Both incident ID and evidence ID are set.
     */
    EVIDENCE_EXPLORATION,
    
    /**
     * Operator is replaying timeline events for an incident.
     * Incident ID and replay checkpoint are set.
     */
    TIMELINE_REPLAY,
    
    /**
     * Operator is analyzing correlated incidents.
     * Correlation ID is set in workspace state.
     */
    CORRELATION_ANALYSIS;
    
    /**
     * Returns true if this context requires an active incident.
     */
    public boolean requiresIncident() {
        return this != INCIDENT_LIST;
    }
    
    /**
     * Returns true if this context supports evidence exploration.
     */
    public boolean supportsEvidence() {
        return this == INCIDENT_DETAIL || this == EVIDENCE_EXPLORATION;
    }
    
    /**
     * Returns true if this context supports timeline replay.
     */
    public boolean supportsReplay() {
        return this == INCIDENT_DETAIL || this == TIMELINE_REPLAY;
    }
    
    /**
     * Returns true if this context supports correlation analysis.
     */
    public boolean supportsCorrelation() {
        return this == INCIDENT_DETAIL || this == CORRELATION_ANALYSIS;
    }
}
