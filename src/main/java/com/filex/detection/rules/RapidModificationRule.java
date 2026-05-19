package com.filex.detection.rules;

import com.filex.detection.*;
import com.filex.engine.RawFileModifiedEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Detects rapid file modification activity.
 *
 * <p>Triggers when files are modified at an unusually high rate, which may indicate encryption
 * activity or automated tampering.
 *
 * <p>Thresholds:
 *
 * <ul>
 *   <li>100+ modifications in 10 seconds → HIGH severity
 *   <li>50-99 modifications in 10 seconds → MEDIUM severity
 * </ul>
 */
public final class RapidModificationRule implements DetectionRule {

  private static final Logger log = LoggerFactory.getLogger(RapidModificationRule.class);

  private static final long EVALUATION_WINDOW_MS = 10_000; // 10 seconds
  private static final int HIGH_SEVERITY_THRESHOLD = 100;
  private static final int MEDIUM_SEVERITY_THRESHOLD = 50;

  private volatile boolean enabled = true;

  @Override
  public String name() {
    return "RapidModificationRule";
  }

  @Override
  public String description() {
    return "Detects rapid file modification activity that may indicate encryption or tampering";
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
    // Only evaluate on MODIFY events
    if (!(context.currentEvent() instanceof RawFileModifiedEvent)) {
      return DetectionResult.notDetected();
    }

    // Count recent modifications within window
    List<RawFileModifiedEvent> recentModifications =
        context.getEventsWithinWindow(EVALUATION_WINDOW_MS).stream()
            .filter(e -> e instanceof RawFileModifiedEvent)
            .map(e -> (RawFileModifiedEvent) e)
            .collect(Collectors.toList());

    int modificationCount = recentModifications.size();

    if (modificationCount >= HIGH_SEVERITY_THRESHOLD) {
      log.warn(
          "Rapid modification detected: {} files modified in {}ms",
          modificationCount,
          EVALUATION_WINDOW_MS);

      Map<String, Object> contextData = new HashMap<>();
      contextData.put("modificationCount", modificationCount);
      contextData.put("windowMs", EVALUATION_WINDOW_MS);

      return DetectionResult.detected(
          Severity.HIGH,
          Confidence.MEDIUM,
          String.format(
              "Rapid modification detected: %d files modified in %d seconds",
              modificationCount, EVALUATION_WINDOW_MS / 1000),
          contextData);
    } else if (modificationCount >= MEDIUM_SEVERITY_THRESHOLD) {
      log.info(
          "Elevated modification activity detected: {} files modified in {}ms",
          modificationCount,
          EVALUATION_WINDOW_MS);

      Map<String, Object> contextData = new HashMap<>();
      contextData.put("modificationCount", modificationCount);
      contextData.put("windowMs", EVALUATION_WINDOW_MS);

      return DetectionResult.detected(
          Severity.MEDIUM,
          Confidence.MEDIUM,
          String.format(
              "Elevated modification activity: %d files modified in %d seconds",
              modificationCount, EVALUATION_WINDOW_MS / 1000),
          contextData);
    }

    return DetectionResult.notDetected();
  }
}
