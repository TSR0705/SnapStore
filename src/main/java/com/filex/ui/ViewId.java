package com.filex.ui;

/**
 * Enumeration of all navigable views in the application.
 *
 * <p>Each entry maps a logical view identifier to its FXML resource path.
 * This is the single source of truth for navigation targets — no string
 * literals for view paths should exist outside this enum.
 *
 * <p>Phase 1A defines only the structural shell views. Feature views
 * (Dashboard, Alerts, Reports, etc.) are added in later phases.
 */
public enum ViewId {

    /**
     * The main application shell — sidebar + content area.
     * Loaded once at startup; not navigated to directly.
     */
    MAIN_LAYOUT("/fxml/MainLayout.fxml"),

    /**
     * Default landing view shown after bootstrap completes.
     * Placeholder for Phase 1A — replaced by Dashboard in Phase 2.
     */
    OVERVIEW("/fxml/OverviewView.fxml");

    // Future phase views (commented out — not implemented yet):
    // DASHBOARD("/fxml/DashboardView.fxml"),
    // ALERTS("/fxml/AlertsView.fxml"),
    // REPORTS("/fxml/ReportsView.fxml"),
    // SETTINGS("/fxml/SettingsView.fxml"),
    // ENDPOINT_DETAIL("/fxml/EndpointDetailView.fxml");

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
