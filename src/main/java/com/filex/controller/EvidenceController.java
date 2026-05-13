package com.filex.controller;

import com.filex.app.AppContext;
import com.filex.investigation.EvidenceSummary;
import com.filex.investigation.InvestigationResult;
import com.filex.workspace.NavigationContext;
import com.filex.workspace.WorkspaceService;
import com.filex.workspace.WorkspaceState;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;

/**
 * Controller for evidence exploration view.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Display evidence list for incident</li>
 *   <li>Show evidence details</li>
 *   <li>Support evidence traversal (future)</li>
 *   <li>Handle async evidence loading</li>
 * </ul>
 *
 * <p>Design principles:
 * <ul>
 *   <li><b>Bounded rendering</b> — limits visible items</li>
 *   <li><b>Async loading</b> — never blocks UI thread</li>
 *   <li><b>Traversal safety</b> — depth limits, loop prevention</li>
 *   <li><b>NO direct repository access</b> — uses WorkspaceService only</li>
 * </ul>
 *
 * <p>Traversal safeguards:
 * <ul>
 *   <li>Maximum traversal depth: 5</li>
 *   <li>Visited tracking prevents loops</li>
 *   <li>Explicit relationships only (no inference)</li>
 * </ul>
 */
public final class EvidenceController {

    private static final Logger log = LoggerFactory.getLogger(EvidenceController.class);
    private static final DateTimeFormatter TIMESTAMP_FORMAT = 
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final int MAX_TRAVERSAL_DEPTH = 5;

    private final AppContext appContext;
    private final WorkspaceService workspaceService;

    @FXML private VBox evidenceRoot;
    @FXML private Label incidentIdLabel;
    @FXML private ListView<EvidenceSummary> evidenceListView;
    @FXML private TextArea evidenceDetailArea;
    @FXML private Label traversalDepthLabel;
    @FXML private Button backButton;
    @FXML private ProgressIndicator loadingIndicator;
    @FXML private Label errorLabel;
    @FXML private Label statusLabel;

    private final ObservableList<EvidenceSummary> evidenceItems = FXCollections.observableArrayList();
    private final Set<String> visitedEvidence = new HashSet<>();
    private int currentTraversalDepth = 0;
    private String currentIncidentId;
    private InvestigationWorkspaceController workspaceController;

    /**
     * Constructor-based dependency injection.
     */
    public EvidenceController(AppContext appContext, WorkspaceService workspaceService) {
        this.appContext = appContext;
        this.workspaceService = workspaceService;
    }

    /**
     * JavaFX lifecycle method — called after FXML injection.
     */
    @FXML
    public void initialize() {
        log.debug("EvidenceController initialized.");

        // Configure evidence list
        evidenceListView.setItems(evidenceItems);
        evidenceListView.setCellFactory(lv -> new EvidenceListCell());
        evidenceListView.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> onEvidenceSelected(newVal)
        );

        // Wire up buttons
        backButton.setOnAction(e -> navigateBack());

        // Load evidence from workspace state
        WorkspaceState state = workspaceService.currentState();
        state.activeIncidentId().ifPresent(this::loadEvidence);
    }

    /**
     * Sets the workspace controller reference for navigation.
     * Called by InvestigationWorkspaceController after loading this view.
     */
    public void setWorkspaceController(InvestigationWorkspaceController workspaceController) {
        this.workspaceController = workspaceController;
    }

    /**
     * Loads evidence for incident asynchronously.
     *
     * @param incidentId incident ID
     */
    public void loadEvidence(String incidentId) {
        this.currentIncidentId = incidentId;
        this.currentTraversalDepth = 0;
        this.visitedEvidence.clear();
        
        incidentIdLabel.setText("Incident: " + incidentId);
        setLoading(true);
        clearError();

        workspaceService.findEvidenceForIncidentAsync(
                incidentId,
                this::displayEvidence,
                error -> showError("Failed to load evidence: " + error.getMessage())
        );
    }

    /**
     * Displays evidence list in UI.
     */
    private void displayEvidence(InvestigationResult<EvidenceSummary> result) {
        Platform.runLater(() -> {
            evidenceItems.clear();
            evidenceItems.addAll(result.getItems());
            
            // Track visited evidence
            result.getItems().forEach(e -> visitedEvidence.add(e.getEvidenceId()));
            
            setLoading(false);
            updateTraversalDepth();
            statusLabel.setText(String.format("Loaded %d evidence items", result.getItemCount()));
            
            log.info("Evidence displayed: {} items for incident {}", 
                    result.getItemCount(), currentIncidentId);
        });
    }

    /**
     * Handles evidence selection.
     */
    private void onEvidenceSelected(EvidenceSummary evidence) {
        if (evidence == null) {
            evidenceDetailArea.clear();
            return;
        }

        // Display evidence details
        StringBuilder details = new StringBuilder();
        details.append("Evidence ID: ").append(evidence.getEvidenceId()).append("\n");
        details.append("Rule Name: ").append(evidence.getRuleName()).append("\n");
        details.append("File Path: ").append(evidence.getFilePath()).append("\n");
        details.append("Detected At: ").append(formatTimestamp(evidence.getDetectedAt())).append("\n");
        details.append("Severity: ").append(evidence.getSeverity()).append("\n");
        details.append("Confidence: ").append(evidence.getConfidence()).append("\n");
        
        evidenceDetailArea.setText(details.toString());
        
        log.debug("Evidence selected: {}", evidence.getEvidenceId());
    }

    /**
     * Navigates back to previous view.
     */
    private void navigateBack() {
        cleanup();
        
        boolean success = workspaceService.navigateBack();
        if (success) {
            log.info("Navigated back from evidence exploration");
            
            // Reload the previous view based on navigation context
            WorkspaceState state = workspaceService.currentState();
            if (workspaceController != null && state.navigationContext() == NavigationContext.INCIDENT_DETAIL) {
                state.activeIncidentId().ifPresent(incidentId -> {
                    workspaceController.loadDetailView(com.filex.ui.ViewId.INCIDENT_DETAIL, controller -> {
                        if (controller instanceof IncidentDetailController) {
                            ((IncidentDetailController) controller).loadIncident(incidentId);
                            ((IncidentDetailController) controller).setWorkspaceController(workspaceController);
                        }
                    });
                });
            }
        }
    }

    /**
     * Updates traversal depth display.
     */
    private void updateTraversalDepth() {
        traversalDepthLabel.setText(String.format("Depth: %d/%d", 
                currentTraversalDepth, MAX_TRAVERSAL_DEPTH));
        
        if (currentTraversalDepth >= MAX_TRAVERSAL_DEPTH) {
            traversalDepthLabel.setStyle("-fx-text-fill: #d32f2f;");
            log.warn("Maximum traversal depth reached: {}", MAX_TRAVERSAL_DEPTH);
        } else {
            traversalDepthLabel.setStyle("");
        }
    }

    /**
     * Checks if evidence has been visited (loop prevention).
     */
    private boolean isVisited(String evidenceId) {
        return visitedEvidence.contains(evidenceId);
    }

    /**
     * Checks if traversal depth limit reached.
     */
    private boolean isTraversalDepthExceeded() {
        return currentTraversalDepth >= MAX_TRAVERSAL_DEPTH;
    }

    /**
     * Formats timestamp for display.
     */
    private String formatTimestamp(Instant timestamp) {
        return TIMESTAMP_FORMAT.format(timestamp);
    }

    /**
     * Sets loading state.
     */
    private void setLoading(boolean loading) {
        loadingIndicator.setVisible(loading);
        evidenceRoot.setDisable(loading);
    }

    /**
     * Shows error message.
     */
    private void showError(String message) {
        Platform.runLater(() -> {
            setLoading(false);
            errorLabel.setText(message);
            errorLabel.setVisible(true);
            log.error("Evidence error: {}", message);
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
     * Cleanup method called when controller is disposed.
     */
    public void cleanup() {
        evidenceItems.clear();
        visitedEvidence.clear();
        currentTraversalDepth = 0;
        log.debug("EvidenceController cleanup complete");
    }

    /**
     * Custom list cell for evidence items.
     */
    private static class EvidenceListCell extends ListCell<EvidenceSummary> {
        @Override
        protected void updateItem(EvidenceSummary evidence, boolean empty) {
            super.updateItem(evidence, empty);

            if (empty || evidence == null) {
                setText(null);
                setGraphic(null);
            } else {
                setText(String.format("[%s] %s - %s",
                        evidence.getRuleName(),
                        evidence.getFilePath(),
                        TIMESTAMP_FORMAT.format(evidence.getDetectedAt())));
            }
        }
    }
}
