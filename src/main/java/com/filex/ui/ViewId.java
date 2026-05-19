package com.filex.ui;

/**
 * Enumeration of all navigable views in the application.
 *
 * <p>Each entry maps a logical view identifier to its FXML resource path.
 * This is the single source of truth for navigation targets — no string
 * literals for view paths should exist outside this enum.
 */
public enum ViewId {

    /**
     * The main application shell — sidebar + content area.
     * Loaded once at startup; not navigated to directly.
     */
    MAIN_LAYOUT("/fxml/MainLayout.fxml"),

    /**
     * Default landing view shown after bootstrap completes.
     */
    OVERVIEW("/fxml/OverviewView.fxml"),

    /**
     * Investigation workspace view.
     * Operator interface for incident triage, evidence exploration, and timeline replay.
     */
    INVESTIGATION_WORKSPACE("/com/filex/view/investigation-workspace.fxml"),

    /**
     * Incident detail view.
     * Displays incident summary, metadata, and entry points to evidence/replay.
     */
    INCIDENT_DETAIL("/com/filex/view/incident-detail.fxml"),

    /**
     * Evidence exploration view.
     * Displays evidence list and details with traversal safeguards.
     */
    EVIDENCE_EXPLORATION("/com/filex/view/evidence.fxml"),

    /**
     * Timeline replay view.
     * Forensic timeline replay with step controls and deterministic ordering.
     */
    TIMELINE_REPLAY("/com/filex/view/replay.fxml");

    private final String fxmlPath;

    ViewId(String fxmlPath) {
        this.fxmlPath = fxmlPath;
    }

    /**
     * Returns the classpath-relative FXML resource path for this view.
     */
    public String fxmlPath() {
        return fxmlPath;
    }
}
