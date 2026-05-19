package com.filex.controller;

import com.filex.app.AppContext;
import com.filex.investigation.ReplayNavigationService;
import com.filex.investigation.TimelineEventSummary;
import com.filex.investigation.replay.ReplayCursor;
import com.filex.investigation.replay.ReplayDirection;
import com.filex.workspace.NavigationContext;
import com.filex.workspace.WorkspaceService;
import com.filex.workspace.WorkspaceState;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller for timeline replay view.
 *
 * <p>Responsibilities:
 *
 * <ul>
 *   <li>Display timeline events in deterministic order
 *   <li>Support step forward/backward navigation
 *   <li>Support checkpoint jumping
 *   <li>Handle replay interruption recovery
 *   <li>Bounded rendering of replay events
 * </ul>
 *
 * <p>Design principles:
 *
 * <ul>
 *   <li><b>Deterministic ordering</b> — (timestamp, sequence_number)
 *   <li><b>Async loading</b> — never blocks UI thread
 *   <li><b>Bounded rendering</b> — limits visible events
 *   <li><b>Interruption recovery</b> — safe retry on failure
 *   <li><b>NO direct repository access</b> — uses WorkspaceService only
 * </ul>
 *
 * <p>Replay is forensic navigation, NOT media playback.
 */
public final class ReplayController {

  private static final Logger log = LoggerFactory.getLogger(ReplayController.class);
  private static final DateTimeFormatter TIMESTAMP_FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault());
  private static final int MAX_VISIBLE_EVENTS = 500;

  private final AppContext appContext;
  private final WorkspaceService workspaceService;

  @FXML private VBox replayRoot;
  @FXML private Label incidentIdLabel;
  @FXML private ListView<TimelineEventSummary> replayListView;
  @FXML private TextArea eventDetailArea;
  @FXML private Button stepBackwardButton;
  @FXML private Button stepForwardButton;
  @FXML private Button jumpToStartButton;
  @FXML private Button jumpToEndButton;
  @FXML private Button retryButton;
  @FXML private Button backButton;
  @FXML private Label positionLabel;
  @FXML private ProgressIndicator loadingIndicator;
  @FXML private Label errorLabel;
  @FXML private Label statusLabel;

  private final ObservableList<TimelineEventSummary> replayEvents =
      FXCollections.observableArrayList();
  private int currentPosition = 0;
  private String currentIncidentId;
  private ReplayNavigationService.ReplayWindow currentReplayWindow;
  private boolean replayLoaded = false;
  private InvestigationWorkspaceController workspaceController;

  /** Constructor-based dependency injection. */
  public ReplayController(AppContext appContext, WorkspaceService workspaceService) {
    this.appContext = appContext;
    this.workspaceService = workspaceService;
  }

  /** JavaFX lifecycle method — called after FXML injection. */
  @FXML
  public void initialize() {
    log.debug("ReplayController initialized.");

    // Configure replay list
    replayListView.setItems(replayEvents);
    replayListView.setCellFactory(lv -> new ReplayEventListCell());
    replayListView
        .getSelectionModel()
        .selectedItemProperty()
        .addListener((obs, oldVal, newVal) -> onEventSelected(newVal));

    // Wire up buttons
    stepBackwardButton.setOnAction(e -> stepBackward());
    stepForwardButton.setOnAction(e -> stepForward());
    jumpToStartButton.setOnAction(e -> jumpToStart());
    jumpToEndButton.setOnAction(e -> jumpToEnd());
    retryButton.setOnAction(e -> retryLoad());
    backButton.setOnAction(e -> navigateBack());

    // Load replay from workspace state
    WorkspaceState state = workspaceService.currentState();
    state.activeIncidentId().ifPresent(this::loadReplay);
  }

  /**
   * Sets the workspace controller reference for navigation. Called by
   * InvestigationWorkspaceController after loading this view.
   */
  public void setWorkspaceController(InvestigationWorkspaceController workspaceController) {
    this.workspaceController = workspaceController;
  }

  /**
   * Loads replay timeline asynchronously.
   *
   * @param incidentId incident ID
   */
  public void loadReplay(String incidentId) {
    this.currentIncidentId = incidentId;
    this.replayLoaded = false;

    incidentIdLabel.setText("Replaying Incident: " + incidentId);
    setLoading(true);
    clearError();
    hideRetry();

    ReplayCursor startCursor =
        new ReplayCursor(incidentId, Instant.EPOCH, 0, ReplayDirection.FORWARD, MAX_VISIBLE_EVENTS);

    workspaceService.replayFromCheckpointAsync(
        startCursor, this::displayReplay, this::handleReplayFailure);
  }

  /** Displays replay timeline in UI. */
  private void displayReplay(ReplayNavigationService.ReplayWindow replayWindow) {
    Platform.runLater(
        () -> {
          this.currentReplayWindow = replayWindow;
          this.replayLoaded = true;

          replayEvents.clear();

          // Bounded rendering - limit visible events
          List<TimelineEventSummary> events = replayWindow.events();
          int eventCount = Math.min(events.size(), MAX_VISIBLE_EVENTS);

          for (int i = 0; i < eventCount; i++) {
            replayEvents.add(events.get(i));
          }

          if (events.size() > MAX_VISIBLE_EVENTS) {
            log.warn(
                "Replay window exceeds max visible events: {} > {}",
                events.size(),
                MAX_VISIBLE_EVENTS);
          }

          currentPosition = 0;
          updatePosition();
          setLoading(false);

          statusLabel.setText(
              String.format("Loaded %d events (showing %d)", events.size(), eventCount));

          // Auto-select first event
          if (!replayEvents.isEmpty()) {
            replayListView.getSelectionModel().select(0);
          }

          log.info(
              "Replay timeline displayed: {} events for incident {}",
              eventCount,
              currentIncidentId);
        });
  }

  /** Handles replay loading failure with recovery options. */
  private void handleReplayFailure(Throwable error) {
    Platform.runLater(
        () -> {
          setLoading(false);
          showError("Failed to load replay timeline: " + error.getMessage());
          showRetry();

          log.error("Replay loading failed for incident: {}", currentIncidentId, error);
        });
  }

  /** Steps backward in replay timeline. */
  private void stepBackward() {
    if (!replayLoaded || replayEvents.isEmpty()) {
      return;
    }

    if (currentPosition > 0) {
      currentPosition--;
      replayListView.getSelectionModel().select(currentPosition);
      replayListView.scrollTo(currentPosition);
      updatePosition();

      log.debug("Stepped backward to position: {}", currentPosition);
    } else {
      // Need to fetch previous window
      TimelineEventSummary firstEvent = replayEvents.get(0);
      ReplayCursor cursor =
          new ReplayCursor(
              currentIncidentId,
              firstEvent.getTimestamp(),
              firstEvent.getSequenceNumber(),
              ReplayDirection.BACKWARD,
              MAX_VISIBLE_EVENTS);

      setLoading(true);
      workspaceService.replayFromCheckpointAsync(
          cursor,
          window -> {
            displayReplay(window);
            Platform.runLater(
                () -> {
                  // Start at the end of the newly loaded previous window
                  currentPosition = replayEvents.size() - 1;
                  replayListView.getSelectionModel().select(currentPosition);
                  replayListView.scrollTo(currentPosition);
                  updatePosition();
                });
          },
          this::handleReplayFailure);
    }
  }

  /** Steps forward in replay timeline. */
  private void stepForward() {
    if (!replayLoaded || replayEvents.isEmpty()) {
      return;
    }

    if (currentPosition < replayEvents.size() - 1) {
      currentPosition++;
      replayListView.getSelectionModel().select(currentPosition);
      replayListView.scrollTo(currentPosition);
      updatePosition();

      log.debug("Stepped forward to position: {}", currentPosition);
    } else if (currentReplayWindow != null && currentReplayWindow.hasMore()) {
      // Need to fetch next window
      TimelineEventSummary lastEvent = replayEvents.get(replayEvents.size() - 1);
      ReplayCursor cursor =
          new ReplayCursor(
              currentIncidentId,
              lastEvent.getTimestamp(),
              lastEvent.getSequenceNumber(),
              ReplayDirection.FORWARD,
              MAX_VISIBLE_EVENTS);

      setLoading(true);
      workspaceService.replayFromCheckpointAsync(
          cursor, this::displayReplay, this::handleReplayFailure);
    }
  }

  /** Jumps to start of replay timeline (checkpoint). */
  private void jumpToStart() {
    if (!replayLoaded && currentIncidentId == null) {
      return;
    }

    log.info("Jumped to true start of replay timeline");
    ReplayCursor cursor =
        new ReplayCursor(
            currentIncidentId, Instant.EPOCH, 0, ReplayDirection.FORWARD, MAX_VISIBLE_EVENTS);

    setLoading(true);
    workspaceService.replayFromCheckpointAsync(
        cursor, this::displayReplay, this::handleReplayFailure);
  }

  /** Jumps to end of replay timeline (checkpoint). */
  private void jumpToEnd() {
    if (!replayLoaded && currentIncidentId == null) {
      return;
    }

    log.info("Jumped to true end of replay timeline");
    // Use a future timestamp to fetch the very last events backward
    ReplayCursor cursor =
        new ReplayCursor(
            currentIncidentId,
            Instant.ofEpochMilli(Long.MAX_VALUE),
            Long.MAX_VALUE,
            ReplayDirection.BACKWARD,
            MAX_VISIBLE_EVENTS);

    setLoading(true);
    workspaceService.replayFromCheckpointAsync(
        cursor,
        window -> {
          displayReplay(window);
          Platform.runLater(
              () -> {
                // Select the actual last event in the new window
                currentPosition = replayEvents.size() - 1;
                replayListView.getSelectionModel().select(currentPosition);
                replayListView.scrollTo(currentPosition);
                updatePosition();
              });
        },
        this::handleReplayFailure);
  }

  /** Retries loading replay after failure. */
  private void retryLoad() {
    if (currentIncidentId != null) {
      log.info("Retrying replay load for incident: {}", currentIncidentId);
      loadReplay(currentIncidentId);
    }
  }

  /** Handles event selection. */
  private void onEventSelected(TimelineEventSummary event) {
    if (event == null) {
      eventDetailArea.clear();
      return;
    }

    // Update current position based on selection
    int selectedIndex = replayListView.getSelectionModel().getSelectedIndex();
    if (selectedIndex >= 0) {
      currentPosition = selectedIndex;
      updatePosition();
    }

    // Display event details
    StringBuilder details = new StringBuilder();
    details.append("Event Type: ").append(event.getEventType()).append("\n");
    details.append("Timestamp: ").append(formatTimestamp(event.getTimestamp())).append("\n");
    details.append("Sequence: ").append(event.getSequenceNumber()).append("\n");
    details.append("Description: ").append(event.getDescription()).append("\n");
    details.append("Incident ID: ").append(event.getIncidentId()).append("\n");
    details.append("Evidence ID: ").append(event.getEvidenceId()).append("\n");

    eventDetailArea.setText(details.toString());

    log.debug("Event selected: {} at position {}", event.getEventType(), currentPosition);
  }

  /** Navigates back to previous view. */
  private void navigateBack() {
    // Cleanup replay state before navigation
    cleanup();

    boolean success = workspaceService.navigateBack();
    if (success) {
      log.info("Navigated back from replay");

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

  /** Updates position label and button states. */
  private void updatePosition() {
    if (replayEvents.isEmpty()) {
      positionLabel.setText("Position: 0/0");
      stepBackwardButton.setDisable(true);
      stepForwardButton.setDisable(true);
      jumpToStartButton.setDisable(true);
      jumpToEndButton.setDisable(true);
    } else {
      // position is not absolute anymore across the whole incident, but relative to window
      positionLabel.setText(
          String.format(
              "Window Pos: %d/%d (Total: %d)",
              currentPosition + 1,
              replayEvents.size(),
              currentReplayWindow != null ? currentReplayWindow.totalEventCount() : 0));

      // Can always try to fetch backward unless we know we're at the very absolute beginning.
      // For now, enable step backward if not at position 0 OR if we might have previous windows.
      stepBackwardButton.setDisable(false);

      // Disable forward if at end of window AND no more events in db
      boolean atEndOfWindow = currentPosition == replayEvents.size() - 1;
      boolean hasMore = currentReplayWindow != null && currentReplayWindow.hasMore();
      stepForwardButton.setDisable(atEndOfWindow && !hasMore);

      jumpToStartButton.setDisable(false);
      jumpToEndButton.setDisable(false);
    }
  }

  /** Formats timestamp for display. */
  private String formatTimestamp(Instant timestamp) {
    return TIMESTAMP_FORMAT.format(timestamp);
  }

  /** Sets loading state. */
  private void setLoading(boolean loading) {
    loadingIndicator.setVisible(loading);
    replayRoot.setDisable(loading);
  }

  /** Shows error message. */
  private void showError(String message) {
    errorLabel.setText(message);
    errorLabel.setVisible(true);
  }

  /** Clears error message. */
  private void clearError() {
    errorLabel.setVisible(false);
    errorLabel.setText("");
  }

  /** Shows retry button. */
  private void showRetry() {
    retryButton.setVisible(true);
  }

  /** Hides retry button. */
  private void hideRetry() {
    retryButton.setVisible(false);
  }

  /** Cleanup method called when controller is disposed. Implements bounded memory discipline. */
  public void cleanup() {
    replayEvents.clear();
    currentReplayWindow = null;
    currentPosition = 0;
    replayLoaded = false;
    eventDetailArea.clear();

    log.debug("ReplayController cleanup complete - memory released");
  }

  /**
   * Custom list cell for replay events. Shows deterministic ordering: (timestamp, sequence_number).
   */
  private static class ReplayEventListCell extends ListCell<TimelineEventSummary> {
    @Override
    protected void updateItem(TimelineEventSummary event, boolean empty) {
      super.updateItem(event, empty);

      if (empty || event == null) {
        setText(null);
        setGraphic(null);
        setStyle("");
      } else {
        setText(
            String.format(
                "[%s] %s - %s (seq: %d)",
                TIMESTAMP_FORMAT.format(event.getTimestamp()),
                event.getEventType(),
                event.getDescription(),
                event.getSequenceNumber()));

        // Highlight current position
        if (isSelected()) {
          setStyle("-fx-background-color: #e3f2fd;");
        } else {
          setStyle("");
        }
      }
    }
  }
}
