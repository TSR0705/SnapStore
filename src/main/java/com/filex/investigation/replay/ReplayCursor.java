package com.filex.investigation.replay;

import java.time.Instant;
import java.util.Objects;

/** Deterministic replay cursor model for forensic timeline navigation. */
public record ReplayCursor(
    String incidentId,
    Instant timestamp,
    long sequenceNumber,
    ReplayDirection direction,
    int windowSize) {
  public ReplayCursor {
    Objects.requireNonNull(incidentId, "incidentId must not be null");
    Objects.requireNonNull(timestamp, "timestamp must not be null");
    Objects.requireNonNull(direction, "direction must not be null");
    if (windowSize <= 0) {
      throw new IllegalArgumentException("windowSize must be positive");
    }
  }
}
