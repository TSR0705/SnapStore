package com.filex.controller;

import com.filex.app.AppContext;
import com.filex.detection.DetectionRule;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;

/**
 * Controller for the Overview landing view.
 *
 * <p>Redesigned to present a high-contrast real-time Threat surveillance dashboard
 * with live telemetry updates, monitoring targets, active rules list, dynamic host target adding,
 * and a scrolling live Security Operations Terminal log feed.
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
    @FXML private VBox vboxRules;
    @FXML private TextArea txtTerminalLog;

    // Telemetry Statistics Elements
    @FXML private Label lblWatchCount;
    @FXML private Label lblDeduplicated;
    @FXML private Label lblEvaluations;
    @FXML private Label lblSuppressed;

    private Timeline refreshTimeline;

    // References to event consumers to prevent leaks and enable clean unsubscription
    private java.util.function.Consumer<com.filex.engine.RawFileCreatedEvent> fileCreatedSub;
    private java.util.function.Consumer<com.filex.engine.RawFileModifiedEvent> fileModifiedSub;
    private java.util.function.Consumer<com.filex.engine.RawFileDeletedEvent> fileDeletedSub;
    private java.util.function.Consumer<com.filex.engine.RawDirectoryCreatedEvent> dirCreatedSub;

    private java.util.function.Consumer<com.filex.detection.MassDeletionDetectedEvent> massDeletionSub;
    private java.util.function.Consumer<com.filex.detection.RapidModificationDetectedEvent> rapidModSub;
    private java.util.function.Consumer<com.filex.detection.SuspiciousRenameDetectedEvent> renameSub;
    private java.util.function.Consumer<com.filex.detection.HiddenFileDetectedEvent> hiddenSub;
    private java.util.function.Consumer<com.filex.detection.SensitiveDirectoryAccessDetectedEvent> sensitiveSub;

    private java.util.function.Consumer<com.filex.alert.IncidentCreatedEvent> incidentSub;

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

        // Initialize event consumer handlers
        fileCreatedSub = e -> handleRawFileEvent(e, "CREATE");
        fileModifiedSub = e -> handleRawFileEvent(e, "MODIFY");
        fileDeletedSub = e -> handleRawFileEvent(e, "DELETE");
        dirCreatedSub = e -> handleRawFileEvent(e, "DIR_CREATE");

        massDeletionSub = this::handleDetectionEvent;
        rapidModSub = this::handleDetectionEvent;
        renameSub = this::handleDetectionEvent;
        hiddenSub = this::handleDetectionEvent;
        sensitiveSub = this::handleDetectionEvent;

        incidentSub = this::handleIncidentEvent;

        // Register subscribers
        var bus = appContext.eventBus();
        bus.subscribe(com.filex.engine.RawFileCreatedEvent.class, fileCreatedSub);
        bus.subscribe(com.filex.engine.RawFileModifiedEvent.class, fileModifiedSub);
        bus.subscribe(com.filex.engine.RawFileDeletedEvent.class, fileDeletedSub);
        bus.subscribe(com.filex.engine.RawDirectoryCreatedEvent.class, dirCreatedSub);

        bus.subscribe(com.filex.detection.MassDeletionDetectedEvent.class, massDeletionSub);
        bus.subscribe(com.filex.detection.RapidModificationDetectedEvent.class, rapidModSub);
        bus.subscribe(com.filex.detection.SuspiciousRenameDetectedEvent.class, renameSub);
        bus.subscribe(com.filex.detection.HiddenFileDetectedEvent.class, hiddenSub);
        bus.subscribe(com.filex.detection.SensitiveDirectoryAccessDetectedEvent.class, sensitiveSub);

        bus.subscribe(com.filex.alert.IncidentCreatedEvent.class, incidentSub);

        // Execute initial telemetry update
        updateTelemetry();

        // Setup live background refresh timeline (1.5 seconds)
        refreshTimeline = new Timeline(new KeyFrame(Duration.seconds(1.5), event -> {
            // Self-healing clean exit if view is swapped out / detached
            if (lblStatus.getScene() == null) {
                refreshTimeline.stop();
                cleanupSubscriptions();
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
     * Action handler to dynamically add any folder on the computer to recursive surveillance watch.
     */
    @FXML
    public void onAddPath() {
        try {
            DirectoryChooser directoryChooser = new DirectoryChooser();
            directoryChooser.setTitle("Register Host Target Directory");

            // Open directory selection dialog centered on the dashboard scene
            Window ownerWindow = lblStatus.getScene().getWindow();
            File selectedDirectory = directoryChooser.showDialog(ownerWindow);

            if (selectedDirectory != null) {
                Path path = selectedDirectory.toPath();
                appContext.monitoringEngine().addMonitoredPath(path);

                // Print success message to live log terminal
                appendLog("[SYSTEM] Successfully registered new host path under active recursive surveillance: " + path.toAbsolutePath());

                // Update metrics immediately
                updateTelemetry();
            }
        } catch (Exception e) {
            log.error("Failed to dynamically add surveillance target path", e);
            appendLog("[ERROR] Failed to dynamically register path: " + e.getMessage());
        }
    }

    /**
     * Helper to write formatted logs directly into the scrolling text-area console feed.
     */
    private void appendLog(String message) {
        if (txtTerminalLog == null) return;
        String timestamp = DateTimeFormatter.ofPattern("HH:mm:ss").format(LocalTime.now());
        txtTerminalLog.appendText(String.format("[%s] %s%n", timestamp, message));
    }

    /**
     * Decodes and formats raw filesystem event alerts for visual output.
     */
    private void handleRawFileEvent(com.filex.engine.MonitoringEvent event, String op) {
        Platform.runLater(() -> {
            String pathStr = (event.path() != null) ? event.path().toAbsolutePath().normalize().toString() : "n/a";
            appendLog(String.format("[FILE] %-10s - %s", op, pathStr));
        });
    }

    /**
     * Decodes and formats threat signature matches for visual warnings.
     */
    private void handleDetectionEvent(com.filex.detection.DetectionEvent event) {
        Platform.runLater(() -> {
            appendLog(String.format("[ALERT] ⚠️  RULE MATCHED: %s - %s [Severity: %s]",
                    event.ruleName(), event.description(), event.severity()));
        });
    }

    /**
     * Decodes and formats correlated incident alarms for visual highlights.
     */
    private void handleIncidentEvent(com.filex.alert.IncidentCreatedEvent event) {
        Platform.runLater(() -> {
            var inc = event.getIncident();
            appendLog(String.format("[INCIDENT] 🚨 ALARM TRIGGERED: %s (ID: %s) [Severity: %s]",
                    inc.getTitle(), inc.getIncidentId(), inc.getSeverity()));
        });
    }

    /**
     * Performs a clean un-subscription of all EventBus handlers to avoid memory leak conditions.
     */
    private void cleanupSubscriptions() {
        try {
            var bus = appContext.eventBus();
            bus.unsubscribe(com.filex.engine.RawFileCreatedEvent.class, fileCreatedSub);
            bus.unsubscribe(com.filex.engine.RawFileModifiedEvent.class, fileModifiedSub);
            bus.unsubscribe(com.filex.engine.RawFileDeletedEvent.class, fileDeletedSub);
            bus.unsubscribe(com.filex.engine.RawDirectoryCreatedEvent.class, dirCreatedSub);

            bus.unsubscribe(com.filex.detection.MassDeletionDetectedEvent.class, massDeletionSub);
            bus.unsubscribe(com.filex.detection.RapidModificationDetectedEvent.class, rapidModSub);
            bus.unsubscribe(com.filex.detection.SuspiciousRenameDetectedEvent.class, renameSub);
            bus.unsubscribe(com.filex.detection.HiddenFileDetectedEvent.class, hiddenSub);
            bus.unsubscribe(com.filex.detection.SensitiveDirectoryAccessDetectedEvent.class, sensitiveSub);

            bus.unsubscribe(com.filex.alert.IncidentCreatedEvent.class, incidentSub);
            log.debug("OverviewController event bus subscribers cleaned up successfully.");
        } catch (Exception e) {
            log.error("Failed to clean up OverviewController subscribers", e);
        }
    }

    /**
     * Queries engine managers and database persistence layers to update UI elements.
     */
    private void updateTelemetry() {
        try {
            // 1. Fetch live metrics
            var monitoringMetrics = appContext.monitoringEngine().getMetrics();
            var detectionMetrics = appContext.detectionEngine().getMetrics();
            long incidentCount = appContext.incidentPersistenceService().getIncidentCount();
            Set<Path> monitoredRoots = appContext.monitoringEngine().getMonitoredRoots();

            // 2. Set metrics on UI cards
            lblIncidents.setText(String.valueOf(incidentCount));
            lblEvents.setText(String.valueOf(monitoringMetrics.getTotalEventsNormalized()));
            lblPathCount.setText(String.valueOf(monitoringMetrics.getRegisteredPathCount()));
            lblStatus.setText(monitoringMetrics.getCurrentState().toString());

            // 3. Set metrics on Pipeline Telemetry table
            lblWatchCount.setText(String.valueOf(monitoringMetrics.getActiveWatchCount()));
            lblDeduplicated.setText(String.valueOf(monitoringMetrics.getTotalEventsDeduplicated()));
            lblEvaluations.setText(String.valueOf(detectionMetrics.getTotalEvaluations()));
            lblSuppressed.setText(String.valueOf(detectionMetrics.getTotalSuppressedDetections()));

            // Update status styling based on state
            lblStatus.getStyleClass().removeAll("kpi-value-active", "kpi-value-danger", "kpi-value-warning");
            switch (monitoringMetrics.getCurrentState()) {
                case RUNNING -> lblStatus.getStyleClass().add("kpi-value-active");
                case FAILED -> lblStatus.getStyleClass().add("kpi-value-danger");
                default -> lblStatus.getStyleClass().add("kpi-value-warning");
            }

            // 4. Render monitored directory paths
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

            // 5. Render loaded threat detection rules
            vboxRules.getChildren().clear();
            var rules = appContext.detectionEngine().getRules();
            if (rules.isEmpty()) {
                Label noRulesLabel = new Label("No active detection rules loaded.");
                noRulesLabel.getStyleClass().add("monitored-path-item");
                vboxRules.getChildren().add(noRulesLabel);
            } else {
                for (DetectionRule rule : rules) {
                    HBox ruleBox = new HBox(12);
                    ruleBox.getStyleClass().add("rule-item");
                    ruleBox.setAlignment(Pos.CENTER_LEFT);

                    Label lblRuleName = new Label("🛡️  " + rule.name());
                    lblRuleName.getStyleClass().add("rule-name");

                    Label lblRuleStatus = new Label(rule.isEnabled() ? "[ACTIVE]" : "[DISABLED]");
                    lblRuleStatus.getStyleClass().add("rule-status-active");

                    ruleBox.getChildren().addAll(lblRuleName, lblRuleStatus);
                    vboxRules.getChildren().add(ruleBox);
                }
            }

        } catch (Exception e) {
            log.error("Failed to update live dashboard telemetry", e);
        }
    }
}
