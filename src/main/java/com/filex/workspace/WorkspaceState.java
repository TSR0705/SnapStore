package com.filex.workspace;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable snapshot of investigation workspace state.
 *
 * <p>Represents the complete state of an operator's investigation workspace at a specific point in
 * time. Used for:
 *
 * <ul>
 *   <li>State synchronization across UI panels
 *   <li>Session persistence and restoration
 *   <li>Undo/redo foundation (future)
 *   <li>Workspace state debugging
 * </ul>
 *
 * <p>Design principles:
 *
 * <ul>
 *   <li><b>Immutable</b> — thread-safe, no defensive copying needed
 *   <li><b>Minimal</b> — only deterministic state, no UI object graphs
 *   <li><b>Serializable-ready</b> — all fields are primitives or simple types
 *   <li><b>Builder pattern</b> — fluent API for state transitions
 * </ul>
 *
 * <p>Thread-safety: Immutable after construction.
 */
public final class WorkspaceState {

  private final String activeIncidentId;
  private final String activeEvidenceId;
  private final Long activeReplayCheckpoint;
  private final String activeCorrelationId;
  private final NavigationContext navigationContext;
  private final Instant stateTimestamp;
  private final int navigationDepth;

  private WorkspaceState(Builder builder) {
    this.activeIncidentId = builder.activeIncidentId;
    this.activeEvidenceId = builder.activeEvidenceId;
    this.activeReplayCheckpoint = builder.activeReplayCheckpoint;
    this.activeCorrelationId = builder.activeCorrelationId;
    this.navigationContext = builder.navigationContext;
    this.stateTimestamp = builder.stateTimestamp != null ? builder.stateTimestamp : Instant.now();
    this.navigationDepth = builder.navigationDepth;
  }

  // -------------------------------------------------------------------------
  // Getters
  // -------------------------------------------------------------------------

  public Optional<String> activeIncidentId() {
    return Optional.ofNullable(activeIncidentId);
  }

  public Optional<String> activeEvidenceId() {
    return Optional.ofNullable(activeEvidenceId);
  }

  public Optional<Long> activeReplayCheckpoint() {
    return Optional.ofNullable(activeReplayCheckpoint);
  }

  public Optional<String> activeCorrelationId() {
    return Optional.ofNullable(activeCorrelationId);
  }

  public NavigationContext navigationContext() {
    return navigationContext;
  }

  public Instant stateTimestamp() {
    return stateTimestamp;
  }

  public int navigationDepth() {
    return navigationDepth;
  }

  // -------------------------------------------------------------------------
  // State queries
  // -------------------------------------------------------------------------

  public boolean hasActiveIncident() {
    return activeIncidentId != null;
  }

  public boolean hasActiveEvidence() {
    return activeEvidenceId != null;
  }

  public boolean hasActiveReplay() {
    return activeReplayCheckpoint != null;
  }

  public boolean hasActiveCorrelation() {
    return activeCorrelationId != null;
  }

  public boolean isInReplayMode() {
    return hasActiveIncident() && hasActiveReplay();
  }

  public boolean isInEvidenceMode() {
    return hasActiveIncident() && hasActiveEvidence();
  }

  // -------------------------------------------------------------------------
  // Builder
  // -------------------------------------------------------------------------

  public static Builder builder() {
    return new Builder();
  }

  public Builder toBuilder() {
    return new Builder()
        .activeIncidentId(this.activeIncidentId)
        .activeEvidenceId(this.activeEvidenceId)
        .activeReplayCheckpoint(this.activeReplayCheckpoint)
        .activeCorrelationId(this.activeCorrelationId)
        .navigationContext(this.navigationContext)
        .stateTimestamp(this.stateTimestamp)
        .navigationDepth(this.navigationDepth);
  }

  public static final class Builder {
    private String activeIncidentId;
    private String activeEvidenceId;
    private Long activeReplayCheckpoint;
    private String activeCorrelationId;
    private NavigationContext navigationContext = NavigationContext.INCIDENT_LIST;
    private Instant stateTimestamp;
    private int navigationDepth = 0;

    public Builder activeIncidentId(String activeIncidentId) {
      this.activeIncidentId = activeIncidentId;
      return this;
    }

    public Builder activeEvidenceId(String activeEvidenceId) {
      this.activeEvidenceId = activeEvidenceId;
      return this;
    }

    public Builder activeReplayCheckpoint(Long activeReplayCheckpoint) {
      this.activeReplayCheckpoint = activeReplayCheckpoint;
      return this;
    }

    public Builder activeCorrelationId(String activeCorrelationId) {
      this.activeCorrelationId = activeCorrelationId;
      return this;
    }

    public Builder navigationContext(NavigationContext navigationContext) {
      this.navigationContext =
          Objects.requireNonNull(navigationContext, "navigationContext must not be null");
      return this;
    }

    public Builder stateTimestamp(Instant stateTimestamp) {
      this.stateTimestamp = stateTimestamp;
      return this;
    }

    public Builder navigationDepth(int navigationDepth) {
      if (navigationDepth < 0) {
        throw new IllegalArgumentException("navigationDepth must be non-negative");
      }
      this.navigationDepth = navigationDepth;
      return this;
    }

    public WorkspaceState build() {
      return new WorkspaceState(this);
    }
  }

  @Override
  public String toString() {
    return "WorkspaceState{"
        + "activeIncidentId='"
        + activeIncidentId
        + '\''
        + ", activeEvidenceId='"
        + activeEvidenceId
        + '\''
        + ", activeReplayCheckpoint="
        + activeReplayCheckpoint
        + ", activeCorrelationId='"
        + activeCorrelationId
        + '\''
        + ", navigationContext="
        + navigationContext
        + ", stateTimestamp="
        + stateTimestamp
        + ", navigationDepth="
        + navigationDepth
        + '}';
  }
}
