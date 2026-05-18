package com.filex.controller;

import com.filex.app.AppContext;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Set;

/**
 * Controller for the Overview landing view.
 *
 * <p>Redesigned to present a high-contrast real-time Threat surveillance dashboard
 * with live telemetry updates and monitoring targets.
 */
public final class OverviewController {

    private static final Logger log = LoggerFactory.getLogger(OverviewController.class);

    private final AppContext appContext;

    @FXML private Label lblAppName;
    @FXML private Label lblAppVersion;
    @FXML private Label lblStatus;
    @FXML private Label lblEvents;
    @FXML private Label lblIncidents;
    @FXML private Label lblPathCount;
    @FXML private VBox vboxPaths;

    private Timeline refreshTimeline;

    /**
     * Constructor-based dependency injection.
     */
    public OverviewController(AppContext appContext) {
        this.appContext = appContext;
    }

    /**
     * JavaFX lifecycle method — called after FXML injection completes.
     */
    @FXML
    public void initialize() {
        log.debug("OverviewController initialized.");

        lblAppName.setText("FileX Threat Intel");
        lblAppVersion.setText("Surveillance Dashboard Active");

        // Execute initial telemetry update
        updateTelemetry();

        // Setup live background refresh timeline (1.5 seconds)
        refreshTimeline = new Timeline(new KeyFrame(Duration.seconds(1.5), event -> {
            // Self-healing clean exit if view is swapped out / detached
            if (lblStatus.getScene() == null) {
                refreshTimeline.stop();
                log.debug("OverviewController refresh timeline stopped dynamically as view detached.");
                return;
            }
            updateTelemetry();
        }));
        refreshTimeline.setCycleCount(Timeline.INDEFINITE);
        refreshTimeline.play();

        log.info("Overview live dashboard view rendered and active.");
    }

    /**
     * Queries engine managers and database persistence layers to update UI elements.
     */
    private void updateTelemetry() {
        try {
            // 1. Fetch live metrics
            var monitoringMetrics = appContext.monitoringEngine().getMetrics();
            long incidentCount = appContext.incidentPersistenceService().getIncidentCount();
            Set<Path> monitoredRoots = appContext.monitoringEngine().getMonitoredRoots();

            // 2. Set metrics on UI
            lblIncidents.setText(String.valueOf(incidentCount));
            lblEvents.setText(String.valueOf(monitoringMetrics.getTotalEventsNormalized()));
            lblPathCount.setText(String.valueOf(monitoringMetrics.getRegisteredPathCount()));
            lblStatus.setText(monitoringMetrics.getCurrentState().toString());

            // Update status styling based on state
            lblStatus.getStyleClass().removeAll("kpi-value-active", "kpi-value-danger", "kpi-value-warning");
            switch (monitoringMetrics.getCurrentState()) {
                case RUNNING -> lblStatus.getStyleClass().add("kpi-value-active");
                case FAILED -> lblStatus.getStyleClass().add("kpi-value-danger");
                default -> lblStatus.getStyleClass().add("kpi-value-warning");
            }

            // 3. Render monitored directory paths
            vboxPaths.getChildren().clear();
            if (monitoredRoots.isEmpty()) {
                Label noPathsLabel = new Label("No paths configured for active monitoring.");
                noPathsLabel.getStyleClass().add("monitored-path-item");
                vboxPaths.getChildren().add(noPathsLabel);
            } else {
                for (Path root : monitoredRoots) {
                    Label pathLabel = new Label("📂  " + root.toAbsolutePath().normalize());
                    pathLabel.getStyleClass().add("monitored-path-item");
                    vboxPaths.getChildren().add(pathLabel);
                }
            }

        } catch (Exception e) {
            log.error("Failed to update live dashboard telemetry", e);
        }
    }
}
