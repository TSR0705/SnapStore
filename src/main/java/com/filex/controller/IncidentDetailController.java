package com.filex.controller;

import com.filex.app.AppContext;
import com.filex.investigation.EvidenceSummary;
import com.filex.investigation.IncidentSummary;
import com.filex.investigation.InvestigationResult;
import com.filex.workspace.NavigationContext;
import com.filex.workspace.WorkspaceService;
import com.filex.workspace.WorkspaceState;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Controller for incident detail view.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Display incident summary and metadata</li>
 *   <li>Provide entry points to evidence and replay</li>
 *   <li>Show related incidents</li>
 *   <li>Handle async incident loading</li>
 * </ul>
 *
 * <p>Design principles:
 * <ul>
 *   <li><b>Progressive disclosure</b> — summary first, details on demand</li>
 *   <li><b>Async loading</b> — never blocks UI thread</li>
 *   <li><b>Bounded rendering</b> — limits visible items</li>
 *   <li><b>NO direct repository access</b> — uses WorkspaceService only</li>
 * </ul>
 */
public final class IncidentDetailController {

    private static final Logger log = LoggerFactory.getLogger(IncidentDetailController.class);
    private static final DateTimeFormatter TIMESTAMP_FORMAT = 
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final int MAX_EVIDENCE_PREVIEW = 10;

    private final AppContext appContext;
    private final WorkspaceService workspaceService;

    @FXML private VBox detailRoot;
    @FXML private Label incidentIdLabel;
    @FXML private Label titleLabel;
    @FXML private Label severityLabel;
    @FXML private Label confidenceLabel;
    @FXML private Label statusLabel;
    @FXML private Label detectedAtLabel;
    @FXML private Label evidenceCountLabel;
    @FXML private ListView<EvidenceSummary> evidencePreviewList;
    @FXML private Button viewAllEvidenceButton;
    @FXML private Button replayTimelineButton;
    @FXML private Button viewRelatedButton;
    @FXML private ProgressIndicator loadingIndicator;
    @FXML private Label errorLabel;

    private String currentIncidentId;
    private IncidentSummary currentIncident;
    private InvestigationWorkspaceController workspaceController;

    /**
     * Constructor-based dependency injection.
     */
    public IncidentDetailController(AppContext appContext, WorkspaceService workspaceService) {
        this.appContext = appContext;
        this.workspaceService = workspaceService;
    }

    /**
     * JavaFX lifecycle method — called after FXML injection.
     */
    @FXML
    public void initialize() {
        log.debug("IncidentDetailController initialized.");

        // Configure evidence preview list
        evidencePreviewList.setCellFactory(lv -> new EvidenceListCell());

        // Wire up buttons
        viewAllEvidenceButton.setOnAction(e -> navigateToEvidence());
        replayTimelineButton.setOnAction(e -> navigateToReplay());
        viewRelatedButton.setOnAction(e -> navigateToRelated());

        // Load incident from workspace state
        WorkspaceState state = workspaceService.currentState();
        state.activeIncidentId().ifPresent(this::loadIncident);
    }

    /**
     * Sets the workspace controller reference for navigation.
     * Called by InvestigationWorkspaceController after loading this view.
     */
    public void setWorkspaceController(InvestigationWorkspaceController workspaceController) {
        this.workspaceController = workspaceController;
    }

    /**
     * Loads incident details asynchronously.
     *
     * @param incidentId incident ID to load
     */
    public void loadIncident(String incidentId) {
        this.currentIncidentId = incidentId;
        
        setLoading(true);
        clearError();

        workspaceService.findIncidentByIdAsync(
                incidentId,
                result -> result.ifPresentOrElse(
                        this::displayIncident,
                        () -> showError("Incident not found: " + incidentId)
                ),
                error -> showError("Failed to load incident: " + error.getMessage())
        );
    }

    /**
     * Displays incident details in UI.
     */
    private void displayIncident(IncidentSummary incident) {
        this.currentIncident = incident;
        Platform.runLater(() -> {
            incidentIdLabel.setText(incident.getIncidentId());
            titleLabel.setText(incident.getTitle());
            severityLabel.setText(incident.getSeverity());
            confidenceLabel.setText(incident.getConfidence());
            statusLabel.setText(incident.getStatus());
            detectedAtLabel.setText(formatTimestamp(incident.getCreatedAt()));
            evidenceCountLabel.setText(String.valueOf(incident.getEvidenceCount()));

            // Apply severity styling
            severityLabel.setStyle(getSeverityStyle(incident.getSeverity()));

            // Load evidence preview
            loadEvidencePreview(incident.getIncidentId());

            setLoading(false);
            log.info("Incident detail displayed: {}", incident.getIncidentId());
        });
    }

    /**
     * Loads evidence preview (limited to first N items).
     */
    private void loadEvidencePreview(String incidentId) {
        workspaceService.findEvidenceForIncidentAsync(
                incidentId,
                result -> {
                    Platform.runLater(() -> {
                        evidencePreviewList.getItems().clear();
                        
                        // Bounded rendering - show only first MAX_EVIDENCE_PREVIEW items
                        int itemCount = Math.min(result.getItemCount(), MAX_EVIDENCE_PREVIEW);
                        for (int i = 0; i < itemCount; i++) {
                            evidencePreviewList.getItems().add(result.getItems().get(i));
                        }

                        boolean hasMore = result.getItemCount() > MAX_EVIDENCE_PREVIEW;
                        viewAllEvidenceButton.setText(hasMore 
                                ? String.format("View All Evidence (%d)", result.getItemCount())
                                : "View Evidence");
                        viewAllEvidenceButton.setDisable(result.getItemCount() == 0);
                    });
                },
                error -> log.warn("Failed to load evidence preview", error)
        );
    }

    /**
     * Navigates to evidence exploration view.
     */
    private void navigateToEvidence() {
        if (currentIncidentId == null || workspaceController == null) {
            return;
        }

        WorkspaceState newState = workspaceService.currentState().toBuilder()
                .activeIncidentId(currentIncidentId)
                .navigationContext(NavigationContext.EVIDENCE_EXPLORATION)
                .build();
        workspaceService.updateState(newState);

        workspaceController.loadDetailView(com.filex.ui.ViewId.EVIDENCE_EXPLORATION, controller -> {
            if (controller instanceof EvidenceController) {
                ((EvidenceController) controller).loadEvidence(currentIncidentId);
                ((EvidenceController) controller).setWorkspaceController(workspaceController);
            }
        });

        log.info("Navigating to evidence exploration for incident: {}", currentIncidentId);
    }

    /**
     * Navigates to timeline replay view.
     */
    private void navigateToReplay() {
        if (currentIncidentId == null || workspaceController == null) {
            return;
        }

        WorkspaceState newState = workspaceService.currentState().toBuilder()
                .activeIncidentId(currentIncidentId)
                .navigationContext(NavigationContext.TIMELINE_REPLAY)
                .build();
        workspaceService.updateState(newState);

        workspaceController.loadDetailView(com.filex.ui.ViewId.TIMELINE_REPLAY, controller -> {
            if (controller instanceof ReplayController) {
                ((ReplayController) controller).loadReplay(currentIncidentId);
                ((ReplayController) controller).setWorkspaceController(workspaceController);
            }
        });

        log.info("Navigating to timeline replay for incident: {}", currentIncidentId);
    }

    /**
     * Navigates to related incidents view.
     */
    private void navigateToRelated() {
        if (currentIncident == null || workspaceController == null) {
            return;
        }

        String correlationId = currentIncident.getCorrelationId();
        if (correlationId == null || correlationId.isBlank()) {
            showError("No correlation ID found for this incident.");
            return;
        }

        log.info("Navigate to related incidents for correlation ID: {}", correlationId);

        com.filex.investigation.InvestigationCriteria criteria = com.filex.investigation.InvestigationCriteria.builder()
                .correlationId(correlationId)
                .build();

        workspaceController.loadIncidentsByCriteria(criteria);
        
        WorkspaceState newState = workspaceService.currentState().toBuilder()
                .navigationContext(NavigationContext.INCIDENT_LIST)
                .build();
        workspaceService.updateState(newState);
    }

    /**
     * Formats timestamp for display.
     */
    private String formatTimestamp(Instant timestamp) {
        return TIMESTAMP_FORMAT.format(timestamp);
    }

    /**
     * Returns CSS style for severity level.
     */
    private String getSeverityStyle(String severity) {
        return switch (severity) {
            case "CRITICAL" -> "-fx-text-fill: #d32f2f; -fx-font-weight: bold;";
            case "HIGH" -> "-fx-text-fill: #f57c00; -fx-font-weight: bold;";
            case "MEDIUM" -> "-fx-text-fill: #fbc02d;";
            case "LOW" -> "-fx-text-fill: #388e3c;";
            default -> "";
        };
    }

    /**
     * Sets loading state.
     */
    private void setLoading(boolean loading) {
        loadingIndicator.setVisible(loading);
        detailRoot.setDisable(loading);
    }

    /**
     * Shows error message.
     */
    private void showError(String message) {
        Platform.runLater(() -> {
            setLoading(false);
            errorLabel.setText(message);
            errorLabel.setVisible(true);
            log.error("Incident detail error: {}", message);
        });
    }

    /**
     * Clears error message.
     */
    private void clearError() {
        errorLabel.setVisible(false);
        errorLabel.setText("");
    }

    /**
     * Custom list cell for evidence preview.
     */
    private static class EvidenceListCell extends ListCell<EvidenceSummary> {
        @Override
        protected void updateItem(EvidenceSummary evidence, boolean empty) {
            super.updateItem(evidence, empty);

            if (empty || evidence == null) {
                setText(null);
                setGraphic(null);
            } else {
                setText(String.format("%s - %s", 
                        evidence.getRuleName(), 
                        evidence.getFilePath()));
            }
        }
    }
}
