package com.filex.detection;

import java.nio.file.Path;
import java.util.List;

/**
 * Detection event indicating mass file deletion activity.
 *
 * <p>Triggered when a large number of files are deleted within a short time window, which may
 * indicate ransomware or malicious cleanup activity.
 */
public final class MassDeletionDetectedEvent extends DetectionEvent {

  private final int deletionCount;
  private final long windowMs;

  public MassDeletionDetectedEvent(
      String ruleName,
      Severity severity,
      Confidence confidence,
      String description,
      List<Path> affectedPaths,
      int deletionCount,
      long windowMs) {
    super(ruleName, severity, confidence, description, affectedPaths);
    this.deletionCount = deletionCount;
    this.windowMs = windowMs;
  }

  public int deletionCount() {
    return deletionCount;
  }

  public long windowMs() {
    return windowMs;
  }

  @Override
  public String toString() {
    return "MassDeletionDetectedEvent{"
        + "deletionCount="
        + deletionCount
        + ", windowMs="
        + windowMs
        + ", severity="
        + severity()
        + ", confidence="
        + confidence()
        + ", affectedPaths="
        + affectedPaths().size()
        + '}';
  }
}
