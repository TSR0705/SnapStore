package com.filex.workspace;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Persists and restores investigation workspace sessions.
 *
 * <p>Saves minimal deterministic session state to properties file:
 *
 * <ul>
 *   <li>Active incident ID
 *   <li>Active evidence ID
 *   <li>Active replay checkpoint
 *   <li>Navigation context
 * </ul>
 *
 * <p>Does NOT persist:
 *
 * <ul>
 *   <li>JavaFX UI object graphs
 *   <li>Controller instances
 *   <li>Large caches
 * </ul>
 *
 * <p>Thread-safety: NOT thread-safe. Synchronize externally.
 */
public final class WorkspaceSession {

  private static final Logger log = LoggerFactory.getLogger(WorkspaceSession.class);
  private static final String SESSION_FILE_NAME = "workspace-session.properties";
  private static final int SESSION_VERSION = 1;

  private final Path sessionFile;

  public WorkspaceSession(Path dataDirectory) {
    Objects.requireNonNull(dataDirectory, "dataDirectory must not be null");
    this.sessionFile = dataDirectory.resolve(SESSION_FILE_NAME);
  }

  /**
   * Saves workspace state to session file.
   *
   * @param state workspace state to save
   * @throws WorkspaceException if save fails
   */
  public void save(WorkspaceState state) throws WorkspaceException {
    Objects.requireNonNull(state, "state must not be null");

    try {
      Properties props = new Properties();
      props.setProperty("version", String.valueOf(SESSION_VERSION));
      props.setProperty("navigationContext", state.navigationContext().name());
      props.setProperty("navigationDepth", String.valueOf(state.navigationDepth()));
      props.setProperty("savedAt", String.valueOf(Instant.now().toEpochMilli()));

      state.activeIncidentId().ifPresent(id -> props.setProperty("activeIncidentId", id));
      state.activeEvidenceId().ifPresent(id -> props.setProperty("activeEvidenceId", id));
      state
          .activeReplayCheckpoint()
          .ifPresent(cp -> props.setProperty("activeReplayCheckpoint", String.valueOf(cp)));
      state.activeCorrelationId().ifPresent(id -> props.setProperty("activeCorrelationId", id));

      try (var out = Files.newOutputStream(sessionFile)) {
        props.store(out, "FileX Workspace Session");
      }

      log.debug("Workspace session saved: {}", sessionFile);

    } catch (IOException e) {
      throw new WorkspaceException("Failed to save workspace session", e);
    }
  }

  /**
   * Restores workspace state from session file.
   *
   * @return restored workspace state if available
   * @throws WorkspaceException if restore fails
   */
  public Optional<WorkspaceState> restore() throws WorkspaceException {
    if (!Files.exists(sessionFile)) {
      log.debug("No session file found: {}", sessionFile);
      return Optional.empty();
    }

    try {
      Properties props = new Properties();
      try (var in = Files.newInputStream(sessionFile)) {
        props.load(in);
      }

      String versionStr = props.getProperty("version");
      if (versionStr == null || Integer.parseInt(versionStr) != SESSION_VERSION) {
        log.warn("Session version mismatch or missing");
        return Optional.empty();
      }

      String navigationContextStr = props.getProperty("navigationContext");
      if (navigationContextStr == null) {
        log.warn("Session missing navigation context");
        return Optional.empty();
      }

      WorkspaceState.Builder builder =
          WorkspaceState.builder()
              .navigationContext(NavigationContext.valueOf(navigationContextStr))
              .navigationDepth(Integer.parseInt(props.getProperty("navigationDepth", "0")));

      String incidentId = props.getProperty("activeIncidentId");
      if (incidentId != null) {
        builder.activeIncidentId(incidentId);
      }

      String evidenceId = props.getProperty("activeEvidenceId");
      if (evidenceId != null) {
        builder.activeEvidenceId(evidenceId);
      }

      String checkpointStr = props.getProperty("activeReplayCheckpoint");
      if (checkpointStr != null) {
        builder.activeReplayCheckpoint(Long.parseLong(checkpointStr));
      }

      String correlationId = props.getProperty("activeCorrelationId");
      if (correlationId != null) {
        builder.activeCorrelationId(correlationId);
      }

      String savedAtStr = props.getProperty("savedAt");
      if (savedAtStr != null) {
        builder.stateTimestamp(Instant.ofEpochMilli(Long.parseLong(savedAtStr)));
      }

      WorkspaceState state = builder.build();
      log.info("Workspace session restored: {}", sessionFile);
      return Optional.of(state);

    } catch (IOException e) {
      throw new WorkspaceException("Failed to restore workspace session", e);
    } catch (Exception e) {
      log.error("Corrupted session file: {}", sessionFile, e);
      return Optional.empty();
    }
  }

  /** Deletes the session file. */
  public void clear() {
    try {
      if (Files.exists(sessionFile)) {
        Files.delete(sessionFile);
        log.debug("Session file deleted: {}", sessionFile);
      }
    } catch (IOException e) {
      log.warn("Failed to delete session file: {}", sessionFile, e);
    }
  }
}
