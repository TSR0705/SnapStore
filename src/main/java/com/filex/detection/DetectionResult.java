package com.filex.detection;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable result of a detection rule evaluation.
 *
 * <p>Contains detection metadata including severity, confidence,
 * description, and optional context data.
 */
public final class DetectionResult {

    private final boolean detected;
    private final Severity severity;
    private final Confidence confidence;
    private final String description;
    private final Map<String, Object> context;

    private DetectionResult(
            boolean detected,
            Severity severity,
            Confidence confidence,
            String description,
            Map<String, Object> context) {
        this.detected = detected;
        this.severity = severity;
        this.confidence = confidence;
        this.description = description;
        this.context = context != null ? Collections.unmodifiableMap(context) : Map.of();
    }

    /**
     * Creates a detection result indicating suspicious activity was detected.
     */
    public static DetectionResult detected(
            Severity severity,
            Confidence confidence,
            String description) {
        return new DetectionResult(true, severity, confidence, description, null);
    }

    /**
     * Creates a detection result with additional context data.
     */
    public static DetectionResult detected(
            Severity severity,
            Confidence confidence,
            String description,
            Map<String, Object> context) {
        return new DetectionResult(true, severity, confidence, description, context);
    }

    /**
     * Creates a detection result indicating no suspicious activity was detected.
     */
    public static DetectionResult notDetected() {
        return new DetectionResult(false, null, null, null, null);
    }

    public boolean isDetected() {
        return detected;
    }

    public Severity severity() {
        return severity;
    }

    public Confidence confidence() {
        return confidence;
    }

    public String description() {
        return description;
    }

    public Map<String, Object> context() {
        return context;
    }

    @Override
    public String toString() {
        if (!detected) {
            return "DetectionResult{detected=false}";
        }
        return "DetectionResult{" +
                "detected=true" +
                ", severity=" + severity +
                ", confidence=" + confidence +
                ", description='" + description + '\'' +
                ", context=" + context +
                '}';
    }
}
