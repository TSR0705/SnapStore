package com.filex.detection.rules;

import com.filex.detection.*;
import com.filex.engine.RawFileDeletedEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Detects mass file deletion activity.
 *
 * <p>Triggers when a large number of files are deleted within a short time window, which may
 * indicate ransomware or malicious cleanup activity.
 *
 * <p>Thresholds:
 *
 * <ul>
 *   <li>50+ deletions in 10 seconds → HIGH severity
 *   <li>20-49 deletions in 10 seconds → MEDIUM severity
 * </ul>
 */
public final class MassDeletionRule implements DetectionRule {

  private static final Logger log = LoggerFactory.getLogger(MassDeletionRule.class);

  private static final long EVALUATION_WINDOW_MS = 10_000; // 10 seconds
  private static final int HIGH_SEVERITY_THRESHOLD = 50;
  private static final int MEDIUM_SEVERITY_THRESHOLD = 20;

  private volatile boolean enabled = true;

  @Override
  public String name() {
    return "MassDeletionRule";
  }

  @Override
  public String description() {
    return "Detects mass file deletion activity that may indicate ransomware or malicious cleanup";
  }

  @Override
  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  @Override
  public DetectionResult evaluate(DetectionContext context) {
    // Only evaluate on DELETE events
    if (!(context.currentEvent() instanceof RawFileDeletedEvent)) {
      return DetectionResult.notDetected();
    }

    // Count recent deletions within window
    List<RawFileDeletedEvent> recentDeletions =
        context.getEventsWithinWindow(EVALUATION_WINDOW_MS).stream()
            .filter(e -> e instanceof RawFileDeletedEvent)
            .map(e -> (RawFileDeletedEvent) e)
            .collect(Collectors.toList());

    int deletionCount = recentDeletions.size();

    if (deletionCount >= HIGH_SEVERITY_THRESHOLD) {
      log.warn(
          "Mass deletion detected: {} files deleted in {}ms", deletionCount, EVALUATION_WINDOW_MS);

      Map<String, Object> contextData = new HashMap<>();
      contextData.put("deletionCount", deletionCount);
      contextData.put("windowMs", EVALUATION_WINDOW_MS);

      return DetectionResult.detected(
          Severity.HIGH,
          Confidence.HIGH,
          String.format(
              "Mass deletion detected: %d files deleted in %d seconds",
              deletionCount, EVALUATION_WINDOW_MS / 1000),
          contextData);
    } else if (deletionCount >= MEDIUM_SEVERITY_THRESHOLD) {
      log.info(
          "Elevated deletion activity detected: {} files deleted in {}ms",
          deletionCount,
          EVALUATION_WINDOW_MS);

      Map<String, Object> contextData = new HashMap<>();
      contextData.put("deletionCount", deletionCount);
      contextData.put("windowMs", EVALUATION_WINDOW_MS);

      return DetectionResult.detected(
          Severity.MEDIUM,
          Confidence.MEDIUM,
          String.format(
              "Elevated deletion activity: %d files deleted in %d seconds",
              deletionCount, EVALUATION_WINDOW_MS / 1000),
          contextData);
    }

    return DetectionResult.notDetected();
  }
}
