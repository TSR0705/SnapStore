package com.filex.controller;

import com.filex.app.AppContext;
import com.filex.investigation.IncidentSummary;
import com.filex.investigation.InvestigationCriteria;
import com.filex.investigation.InvestigationResult;
import com.filex.workspace.NavigationContext;
import com.filex.workspace.WorkspaceService;
import com.filex.workspace.WorkspaceState;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * Controller for the investigation workspace.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Coordinate incident list rendering</li>
 *   <li>Handle incident selection</li>
 *   <li>Delegate to detail/evidence/replay sub-controllers</li>
 *   <li>Synchronize workspace state</li>
 * </ul>
 *
 * <p>Design principles:
 * <ul>
 *   <li><b>NO direct repository access</b> — uses WorkspaceService only</li>
 *   <li><b>Coordination only</b> — delegates to specialized controllers</li>
 *   <li><b>Async queries</b> — never blocks UI thread</li>
 *   <li><b>State synchronization</b> — updates workspace state on changes</li>
 * </ul>
 *
 * <p>This is NOT a god controller. It coordinates high-level workspace
 * operations and delegates to specialized controllers for details.
 */
public final class InvestigationWorkspaceController {

    private static final Logger log = LoggerFactory.getLogger(InvestigationWorkspaceController.class);

    private final AppContext appContext;
    private final WorkspaceService workspaceService;

    @FXML private BorderPane workspaceRoot;
    @FXML private VBox incidentListPanel;
    @FXML private ListView<IncidentSummary> incidentListView;
    @FXML private Label statusLabel;
    @FXML private Button refreshButton;
    @FXML private ProgressIndicator loadingIndicator;
    @FXML private VBox detailPanel; // Right panel for detail views

    private final ObservableList<IncidentSummary> incidents = FXCollections.observableArrayList();

    /**
     * Constructor-based dependency injection.
     */
    public InvestigationWorkspaceController(AppContext appContext, WorkspaceService workspaceService) {
        this.appContext = appContext;
        this.workspaceService = workspaceService;
    }

    /**
     * JavaFX lifecycle method — called after FXML injection.
     */
    @FXML
    public void initialize() {
        log.debug("InvestigationWorkspaceController initialized.");
        log.info(com.filex.validation.TruthMarkers.TRUTH,
                "component=InvestigationWorkspaceController event=workspace_initialized");
        log.info(com.filex.validation.TruthMarkers.TRUTH,
                "TRUTH stage=UIController action=workspace_initialized");

        // Configure incident list view
        incidentListView.setItems(incidents);
        incidentListView.setCellFactory(lv -> new IncidentListCell());
        incidentListView.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> onIncidentSelected(newVal)
        );

        // Wire up buttons
        refreshButton.setOnAction(e -> loadIncidents());

        // Initial load
        loadIncidents();

        // Transition to incident list context
        workspaceService.transitionTo(NavigationContext.INCIDENT_LIST);

        // Subscribe to runtime state changes to update empty state messaging
        appContext.eventBus().subscribe(com.filex.event.RuntimeStateChangedEvent.class, this::onRuntimeStateChanged);

        // Subscribe to new incidents for live updates
        appContext.eventBus().subscribe(com.filex.alert.IncidentCreatedEvent.class, e -> {
            log.debug(com.filex.validation.TruthMarkers.TRUTH,
                    "component=InvestigationWorkspaceController event=new_incident_subscription_callback incidentId={}",
                    sanitize(e.getIncident().getIncidentId()));
            log.info(com.filex.validation.TruthMarkers.TRUTH,
                    "TRUTH stage=UIController action=live_incident_received incidentId={}",
                    sanitize(e.getIncident().getIncidentId()));
            loadIncidents();
        });

        // Set initial placeholder
        updateEmptyStateMessage(appContext.runtimeManager().getCurrentState());
    }

    // -------------------------------------------------------------------------
    // Incident loading
    // -------------------------------------------------------------------------

    private void loadIncidents() {
        log.debug("Loading incidents...");
        log.debug(com.filex.validation.TruthMarkers.TRUTH,
                "component=InvestigationWorkspaceController event=incident_list_refresh_triggered source=manual");
        log.info(com.filex.validation.TruthMarkers.TRUTH,
                "TRUTH stage=UIController action=refresh_triggered source=manual");
        loadIncidentsByCriteria(InvestigationCriteria.builder().build());
    }

    /**
     * Loads incidents using specific criteria.
     */
    public void loadIncidentsByCriteria(InvestigationCriteria criteria) {
        setLoading(true);
        statusLabel.setText("Loading incidents...");

        workspaceService.findIncidentsAsync(
                criteria,
                0,  // page 0
                100, // page size
                this::onIncidentsLoaded,
                this::onIncidentsLoadFailed
        );
    }

    private void onIncidentsLoaded(InvestigationResult<IncidentSummary> result) {
        Platform.runLater(() -> {
            incidents.clear();
            incidents.addAll(result.getItems());
            
            setLoading(false);
            statusLabel.setText(String.format("Loaded %d incidents", result.getItemCount()));
            
            log.info("Incidents loaded: {} items", result.getItemCount());
            log.info(com.filex.validation.TruthMarkers.TRUTH,
                    "component=InvestigationWorkspaceController event=incident_count_loaded count={}",
                    result.getItemCount());
            log.info(com.filex.validation.TruthMarkers.TRUTH,
                    "component=InvestigationWorkspaceController event=ui_list_update_complete count={}",
                    result.getItemCount());
            log.info(com.filex.validation.TruthMarkers.TRUTH,
                    "TRUTH stage=UIController action=incident_list_updated count={} result=success",
                    result.getItemCount());
        });
    }

    private void onIncidentsLoadFailed(Throwable error) {
        Platform.runLater(() -> {
            setLoading(false);
            statusLabel.setText("Failed to load incidents: " + error.getMessage());
            
            log.error("Failed to load incidents", error);
            log.info(com.filex.validation.TruthMarkers.TRUTH,
                    "TRUTH stage=UIController action=incident_list_updated result=failure err={}",
                    sanitize(error.getMessage()));
            
            showError("Failed to Load Incidents", 
                     "Could not load incidents from database.", 
                     error.getMessage());
        });
    }

    private void onRuntimeStateChanged(com.filex.event.RuntimeStateChangedEvent event) {
        Platform.runLater(() -> updateEmptyStateMessage(event.getNewState()));
    }

    private void updateEmptyStateMessage(com.filex.event.RuntimeState state) {
        String message;
        if (state == com.filex.event.RuntimeState.FAILED) {
            message = "Monitoring failed to start. Check configuration.";
        } else if (state == com.filex.event.RuntimeState.INITIALIZING || state == com.filex.event.RuntimeState.STARTING) {
            message = "System is starting up...";
        } else if (state == com.filex.event.RuntimeState.RUNNING || state == com.filex.event.RuntimeState.DEGRADED) {
            message = "Monitoring active. No incidents detected yet.";
        } else {
            message = "No incidents yet. Monitoring is inactive.";
        }
        
        Label placeholder = new Label(message);
        placeholder.setStyle("-fx-text-fill: #757575; -fx-font-style: italic;");
        incidentListView.setPlaceholder(placeholder);
    }

    // -------------------------------------------------------------------------
    // Incident selection
    // -------------------------------------------------------------------------

    private void onIncidentSelected(IncidentSummary incident) {
        if (incident == null) {
            return;
        }

        log.info("Incident selected: {}", incident.getIncidentId());
        log.info(com.filex.validation.TruthMarkers.TRUTH,
                "component=InvestigationWorkspaceController event=incident_selection incidentId={}",
                sanitize(incident.getIncidentId()));
        log.info(com.filex.validation.TruthMarkers.TRUTH,
                "TRUTH stage=UIController action=incident_selected incidentId={}",
                sanitize(incident.getIncidentId()));

        // Update workspace state
        WorkspaceState newState = workspaceService.currentState().toBuilder()
                .activeIncidentId(incident.getIncidentId())
                .navigationContext(NavigationContext.INCIDENT_DETAIL)
                .build();
        workspaceService.updateState(newState);

        // Load incident detail view
        loadDetailView(com.filex.ui.ViewId.INCIDENT_DETAIL, controller -> {
            if (controller instanceof IncidentDetailController) {
                ((IncidentDetailController) controller).loadIncident(incident.getIncidentId());
                ((IncidentDetailController) controller).setWorkspaceController(this);
            }
        });
        
        statusLabel.setText("Viewing incident: " + incident.getIncidentId());
    }

    /**
     * Loads a view into the detail panel.
     * Public method to allow detail controllers to trigger view transitions.
     *
     * @param viewId the view to load
     * @param controllerCallback callback to configure the loaded controller
     */
    public void loadDetailView(com.filex.ui.ViewId viewId, Consumer<Object> controllerCallback) {
        if (detailPanel == null) {
            log.error("Detail panel not initialized");
            return;
        }

        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    getClass().getResource(viewId.fxmlPath())
            );
            loader.setControllerFactory(cls -> 
                    com.filex.ui.ControllerFactory.create(cls, appContext)
            );
            
            javafx.scene.Parent detailView = loader.load();
            
            // Configure controller
            Object controller = loader.getController();
            if (controllerCallback != null) {
                controllerCallback.accept(controller);
            }
            
            // Replace detail panel content
            detailPanel.getChildren().clear();
            detailPanel.getChildren().add(detailView);
            javafx.scene.layout.VBox.setVgrow(detailView, javafx.scene.layout.Priority.ALWAYS);
            
            log.debug("Loaded detail view: {}", viewId);
            
        } catch (Exception e) {
            log.error("Failed to load detail view: {}", viewId, e);
            showError("Failed to Load View", 
                     "Could not load view: " + viewId, 
                     e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // UI helpers
    // -------------------------------------------------------------------------

    private void setLoading(boolean loading) {
        loadingIndicator.setVisible(loading);
        refreshButton.setDisable(loading);
        incidentListView.setDisable(loading);
    }

    private void showError(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }

    /**
     * Sanitizes a value for structured logging.
     */
    private static String sanitize(Object value) {
        if (value == null) {
            return "null";
        }
        String s = value.toString();
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c) || Character.isISOControl(c)) {
                sb.append('_');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Custom list cell for incidents
    // -------------------------------------------------------------------------

    private static class IncidentListCell extends ListCell<IncidentSummary> {
        @Override
        protected void updateItem(IncidentSummary incident, boolean empty) {
            super.updateItem(incident, empty);

            if (empty || incident == null) {
                setText(null);
                setGraphic(null);
                setStyle("");
            } else {
                setText(String.format("[%s] %s - %s (%d evidence)",
                        incident.getSeverity(),
                        incident.getIncidentId(),
                        incident.getTitle(),
                        incident.getEvidenceCount()));

                // Color-code by severity
                String style = switch (incident.getSeverity()) {
                    case "CRITICAL" -> "-fx-text-fill: #ff5a6a;";
                    case "HIGH" -> "-fx-text-fill: #ff9b50;";
                    case "MEDIUM" -> "-fx-text-fill: #ffe066;";
                    case "LOW" -> "-fx-text-fill: #4ade80;";
                    default -> "";
                };
                setStyle(style);
            }
        }
    }
}
