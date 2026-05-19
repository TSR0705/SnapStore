package com.filex.controller;

import com.filex.app.AppContext;
import com.filex.investigation.EvidenceSummary;
import com.filex.investigation.InvestigationCriteria;
import com.filex.investigation.InvestigationResult;
import com.filex.workspace.NavigationContext;
import com.filex.workspace.WorkspaceService;
import com.filex.workspace.WorkspaceState;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller for evidence exploration view.
 *
 * <p>Responsibilities:
 *
 * <ul>
 *   <li>Display evidence list for incident
 *   <li>Show evidence details
 *   <li>Support evidence traversal (future)
 *   <li>Handle async evidence loading
 * </ul>
 *
 * <p>Design principles:
 *
 * <ul>
 *   <li><b>Bounded rendering</b> — limits visible items
 *   <li><b>Async loading</b> — never blocks UI thread
 *   <li><b>Traversal safety</b> — depth limits, loop prevention
 *   <li><b>NO direct repository access</b> — uses WorkspaceService only
 * </ul>
 *
 * <p>Traversal safeguards:
 *
 * <ul>
 *   <li>Maximum traversal depth: 5
 *   <li>Visited tracking prevents loops
 *   <li>Explicit relationships only (no inference)
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
  @FXML private Button traverseButton;

  // SaaS Styled GUI Elements
  @FXML private Label lblSaaSAlertSeverity;
  @FXML private Label lblSaaSAlertConfidence;
  @FXML private Label lblSaaSTriggeredPolicy;
  @FXML private Label lblSaaSTargetFile;
  @FXML private Label lblSaaSHostPath;
  @FXML private Label lblSaaSDetectedTime;
  @FXML private Label lblSaaSDescription;
  @FXML private Label lblSaaSPlaybook;

  private final ObservableList<EvidenceSummary> evidenceItems = FXCollections.observableArrayList();
  private final Set<String> visitedEvidence = new HashSet<>();
  private int currentTraversalDepth = 0;
  private String currentIncidentId;
  private InvestigationWorkspaceController workspaceController;
  private EvidenceSummary selectedEvidence;

  /** Constructor-based dependency injection. */
  public EvidenceController(AppContext appContext, WorkspaceService workspaceService) {
    this.appContext = appContext;
    this.workspaceService = workspaceService;
  }

  /** JavaFX lifecycle method — called after FXML injection. */
  @FXML
  public void initialize() {
    log.debug("EvidenceController initialized.");

    // Configure evidence list
    evidenceListView.setItems(evidenceItems);
    evidenceListView.setCellFactory(lv -> new EvidenceListCell());
    evidenceListView
        .getSelectionModel()
        .selectedItemProperty()
        .addListener((obs, oldVal, newVal) -> onEvidenceSelected(newVal));

    // Wire up buttons
    backButton.setOnAction(e -> navigateBack());
    if (traverseButton != null) {
      traverseButton.setOnAction(e -> traverseRelatedEvidence());
    }

    // Load evidence from workspace state
    WorkspaceState state = workspaceService.currentState();
    state.activeIncidentId().ifPresent(this::loadEvidence);
  }

  /**
   * Sets the workspace controller reference for navigation. Called by
   * InvestigationWorkspaceController after loading this view.
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
        error -> showError("Failed to load evidence: " + error.getMessage()));
  }

  /** Displays evidence list in UI. */
  private void displayEvidence(InvestigationResult<EvidenceSummary> result) {
    Platform.runLater(
        () -> {
          evidenceItems.clear();
          evidenceItems.addAll(result.getItems());

          // Track visited evidence
          result.getItems().forEach(e -> visitedEvidence.add(e.getEvidenceId()));

          setLoading(false);
          updateTraversalDepth();
          statusLabel.setText(String.format("Loaded %d evidence items", result.getItemCount()));

          log.info(
              "Evidence displayed: {} items for incident {}",
              result.getItemCount(),
              currentIncidentId);
        });
  }

  /** Handles evidence selection. */
  private void onEvidenceSelected(EvidenceSummary evidence) {
    this.selectedEvidence = evidence;

    if (evidence == null) {
      evidenceDetailArea.clear();
      if (lblSaaSAlertSeverity != null) lblSaaSAlertSeverity.setText("-");
      if (lblSaaSAlertConfidence != null) lblSaaSAlertConfidence.setText("-");
      if (lblSaaSTriggeredPolicy != null) lblSaaSTriggeredPolicy.setText("-");
      if (lblSaaSTargetFile != null) lblSaaSTargetFile.setText("-");
      if (lblSaaSHostPath != null) lblSaaSHostPath.setText("-");
      if (lblSaaSDetectedTime != null) lblSaaSDetectedTime.setText("-");
      if (lblSaaSDescription != null)
        lblSaaSDescription.setText(
            "Select an evidence item to display the automated security analysis.");
      if (lblSaaSPlaybook != null)
        lblSaaSPlaybook.setText("Follow suggested operations to secure target paths.");
      if (traverseButton != null) {
        traverseButton.setDisable(true);
      }
      return;
    }

    // Display evidence details as a beautiful threat briefing report
    StringBuilder details = new StringBuilder();
    details.append("====================================================\n");
    details.append("         🛡️  FILEX SURVEILLANCE THREAT BRIEFING        \n");
    details.append("====================================================\n\n");

    details.append("🔴 ALERT SEVERITY  : [").append(evidence.getSeverity()).append("]\n");
    details.append("🟢 CONFIDENCE RATE : [").append(evidence.getConfidence()).append("]\n\n");

    String cleanFilename = "unknown_file";
    try {
      cleanFilename = java.nio.file.Paths.get(evidence.getFilePath()).getFileName().toString();
    } catch (Exception ex) {
      cleanFilename = evidence.getFilePath();
    }

    details.append("📄 TARGET FILE NAME: ").append(cleanFilename).append("\n");
    details.append("📂 FULL HOST PATH  : ").append(evidence.getFilePath()).append("\n");
    details
        .append("⏰ DETECTED TIME   : ")
        .append(formatTimestamp(evidence.getDetectedAt()))
        .append("\n");
    details.append("🛡️ TRIGGERED POLICY: ").append(evidence.getRuleName()).append("\n\n");

    details.append("----------------------------------------------------\n");
    details.append("🛡️ THREAT DESCRIPTION & ANALYSIS:\n");

    String desc = "Decoy file accessed under active surveillance watcher.";
    String playbook = "Monitor event telemetry.";

    String ruleName = evidence.getRuleName();
    if (ruleName.contains("SensitiveDirectory")) {
      desc =
          "Suspicious modification or write attempt in a high-value secure system configuration folder. This is a common tactic for establishing persistence (e.g. SSH backdoor injection).";
      playbook =
          "1. Review SSH / system authorization access lists immediately.\n2. Revoke any newly injected credentials or unexpected key files.\n3. Validate parent process origin to verify identity.";
    } else if (ruleName.contains("RapidModification")) {
      desc =
          "High-frequency file mutation burst detected across multiple documents in a tight execution window. This is highly indicative of ransomware actively encrypting user files.";
      playbook =
          "1. Quarantine the affected host directory or isolate the network segment.\n2. Terminate the encrypting process thread immediately.\n3. Restore data from immutable sandbox or volume shadow snapshots.";
    } else if (ruleName.contains("MassDeletion")) {
      desc =
          "Rapid purge deletion of multiple documents detected in a short time frame. Typically used by destructive malware or ransomware clearing evidence.";
      playbook =
          "1. Stop host filesystem I/O operations.\n2. Check host deletion logs to identify the triggering executable.\n3. Perform data restoration drills.";
    } else if (ruleName.contains("SuspiciousExtension")) {
      desc =
          "A high-value user document was renamed to a lock extension (like .locked or .crypto). Classic ransomware encryption signature.";
      playbook =
          "1. Identify the encrypting parent process.\n2. Prevent process propagation.\n3. Revert extension rename and restore clean files.";
    } else if (ruleName.contains("HiddenFile")) {
      desc =
          "Stealthy hidden file or dotfile created in monitored user directory. Often used by rootkits or malware hiding config files.";
      playbook =
          "1. Inspect the contents of the hidden file.\n2. Delete the payload if unauthorized.\n3. Perform full anti-malware system scan.";
    }

    details.append(desc).append("\n\n");
    details.append("----------------------------------------------------\n");
    details.append("🚨 RECOMMENDED INCIDENT PLAYBOOK:\n");
    details.append(playbook).append("\n");
    details.append("====================================================\n");

    evidenceDetailArea.setText(details.toString());

    // Dynamic SaaS layout population
    if (lblSaaSAlertSeverity != null) {
      lblSaaSAlertSeverity.setText(evidence.getSeverity());
      String color =
          switch (evidence.getSeverity()) {
            case "CRITICAL", "HIGH" -> "#ef4444";
            case "MEDIUM" -> "#fbbf24";
            default -> "#10b981";
          };
      lblSaaSAlertSeverity.setStyle("-fx-background-color: " + color + "; -fx-text-fill: black;");
    }
    if (lblSaaSAlertConfidence != null) {
      lblSaaSAlertConfidence.setText(evidence.getConfidence());
    }

    String friendlyPolicy = evidence.getRuleName();
    if (friendlyPolicy.contains("SensitiveDirectory")) {
      friendlyPolicy = "Host Directory Alteration Attempt";
    } else if (friendlyPolicy.contains("RapidModification")) {
      friendlyPolicy = "Ransomware File Encryption Burst";
    } else if (friendlyPolicy.contains("MassDeletion")) {
      friendlyPolicy = "High-Velocity File Deletion Threat";
    } else if (friendlyPolicy.contains("SuspiciousExtension")) {
      friendlyPolicy = "Suspicious Extension Locking Event";
    } else if (friendlyPolicy.contains("HiddenFile")) {
      friendlyPolicy = "Stealthy Hidden Payload Created";
    }

    if (lblSaaSTriggeredPolicy != null) lblSaaSTriggeredPolicy.setText(friendlyPolicy);
    if (lblSaaSTargetFile != null) lblSaaSTargetFile.setText(cleanFilename);
    if (lblSaaSHostPath != null) lblSaaSHostPath.setText(evidence.getFilePath());
    if (lblSaaSDetectedTime != null)
      lblSaaSDetectedTime.setText(formatTimestamp(evidence.getDetectedAt()));
    if (lblSaaSDescription != null) lblSaaSDescription.setText(desc);
    if (lblSaaSPlaybook != null) lblSaaSPlaybook.setText(playbook);

    if (traverseButton != null) {
      // Enable traversal if correlation ID exists, we haven't visited it, and depth isn't exceeded
      boolean canTraverse =
          evidence.getCorrelationId() != null
              && !evidence.getCorrelationId().isBlank()
              && !isTraversalDepthExceeded()
              && !isVisited(
                  evidence
                      .getCorrelationId()); // treat correlation ID as the visited marker for fanout
      // protection
      traverseButton.setDisable(!canTraverse);
    }

    log.debug("Evidence selected: {}", evidence.getEvidenceId());
  }

  /** Traverses to related evidence. */
  private void traverseRelatedEvidence() {
    if (selectedEvidence == null || selectedEvidence.getCorrelationId() == null) {
      return;
    }

    String correlationId = selectedEvidence.getCorrelationId();
    visitedEvidence.add(correlationId); // record visit
    currentTraversalDepth++;

    setLoading(true);
    clearError();

    InvestigationCriteria criteria =
        InvestigationCriteria.builder().correlationId(correlationId).build();

    workspaceService.findEvidenceAsync(
        criteria,
        0,
        50, // Limit oversized fanout
        this::displayEvidence,
        error -> showError("Failed to traverse evidence: " + error.getMessage()));

    log.info(
        "Traversing to related evidence by correlation ID: {}, depth: {}",
        correlationId,
        currentTraversalDepth);
  }

  /** Navigates back to previous view. */
  private void navigateBack() {
    cleanup();

    boolean success = workspaceService.navigateBack();
    if (success) {
      log.info("Navigated back from evidence exploration");

      // Reload the previous view based on navigation context
      WorkspaceState state = workspaceService.currentState();
      if (workspaceController != null
          && state.navigationContext() == NavigationContext.INCIDENT_DETAIL) {
        state
            .activeIncidentId()
            .ifPresent(
                incidentId -> {
                  workspaceController.loadDetailView(
                      com.filex.ui.ViewId.INCIDENT_DETAIL,
                      controller -> {
                        if (controller instanceof IncidentDetailController) {
                          ((IncidentDetailController) controller).loadIncident(incidentId);
                          ((IncidentDetailController) controller)
                              .setWorkspaceController(workspaceController);
                        }
                      });
                });
      }
    }
  }

  /** Updates traversal depth display. */
  private void updateTraversalDepth() {
    traversalDepthLabel.setText(
        String.format("Depth: %d/%d", currentTraversalDepth, MAX_TRAVERSAL_DEPTH));

    if (currentTraversalDepth >= MAX_TRAVERSAL_DEPTH) {
      traversalDepthLabel.setStyle("-fx-text-fill: #d32f2f;");
      log.warn("Maximum traversal depth reached: {}", MAX_TRAVERSAL_DEPTH);
    } else {
      traversalDepthLabel.setStyle("");
    }
  }

  /** Checks if evidence has been visited (loop prevention). */
  private boolean isVisited(String evidenceId) {
    return visitedEvidence.contains(evidenceId);
  }

  /** Checks if traversal depth limit reached. */
  private boolean isTraversalDepthExceeded() {
    return currentTraversalDepth >= MAX_TRAVERSAL_DEPTH;
  }

  /** Formats timestamp for display. */
  private String formatTimestamp(Instant timestamp) {
    return TIMESTAMP_FORMAT.format(timestamp);
  }

  /** Sets loading state. */
  private void setLoading(boolean loading) {
    loadingIndicator.setVisible(loading);
    evidenceRoot.setDisable(loading);
  }

  /** Shows error message. */
  private void showError(String message) {
    Platform.runLater(
        () -> {
          setLoading(false);
          errorLabel.setText(message);
          errorLabel.setVisible(true);
          log.error("Evidence error: {}", message);
        });
  }

  /** Clears error message. */
  private void clearError() {
    errorLabel.setVisible(false);
    errorLabel.setText("");
  }

  /** Cleanup method called when controller is disposed. */
  public void cleanup() {
    evidenceItems.clear();
    visitedEvidence.clear();
    currentTraversalDepth = 0;
    log.debug("EvidenceController cleanup complete");
  }

  /** Custom list cell for evidence items. */
  private static class EvidenceListCell extends ListCell<EvidenceSummary> {
    @Override
    protected void updateItem(EvidenceSummary evidence, boolean empty) {
      super.updateItem(evidence, empty);

      if (empty || evidence == null) {
        setText(null);
        setGraphic(null);
        setStyle("");
      } else {
        setText(null);

        // Container
        javafx.scene.layout.HBox cellContainer = new javafx.scene.layout.HBox(10);
        cellContainer.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        cellContainer.setPadding(new javafx.geometry.Insets(4, 6, 4, 6));

        // 1. Icon indicator based on rule
        String rule = evidence.getRuleName();
        String icon = "📁";
        if (rule.contains("SensitiveDirectory")) icon = "🛡️";
        else if (rule.contains("RapidModification")) icon = "💥";
        else if (rule.contains("MassDeletion")) icon = "🗑️";
        else if (rule.contains("SuspiciousExtension")) icon = "🔄";
        else if (rule.contains("HiddenFile")) icon = "🕵️";

        Label lblIcon = new Label(icon);
        lblIcon.setStyle("-fx-font-size: 16px;");

        // 2. Text layout (Filename + short relative directory)
        javafx.scene.layout.VBox textContainer = new javafx.scene.layout.VBox(2);
        javafx.scene.layout.HBox.setHgrow(textContainer, javafx.scene.layout.Priority.ALWAYS);

        // Extract filename
        String fullPath = evidence.getFilePath();
        String filename = "unknown_file";
        String parentDir = "";
        try {
          java.nio.file.Path p = java.nio.file.Paths.get(fullPath);
          filename = p.getFileName().toString();
          parentDir = ".../" + p.getParent().getFileName().toString() + "/";
        } catch (Exception ex) {
          filename = fullPath;
        }

        Label lblFilename = new Label(filename);
        lblFilename.setStyle(
            "-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: -color-text;");

        String timeStr = TIMESTAMP_FORMAT.format(evidence.getDetectedAt());
        Label lblDetails =
            new Label(String.format("Location: %s | Time: %s", parentDir + filename, timeStr));
        lblDetails.setStyle("-fx-font-size: 10.5px; -fx-text-fill: #a0aec0;");

        textContainer.getChildren().addAll(lblFilename, lblDetails);
        cellContainer.getChildren().addAll(lblIcon, textContainer);

        setGraphic(cellContainer);
        setStyle("-fx-background-color: transparent;");
      }
    }
  }
}
