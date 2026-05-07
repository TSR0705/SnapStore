package com.filex.detection;

/**
 * Immutable snapshot of detection engine runtime metrics.
 *
 * <p>Provides observability into detection performance and behavior.
 */
public final class DetectionMetrics {

    private final int registeredRuleCount;
    private final int enabledRuleCount;
    private final long totalEvaluations;
    private final long totalDetections;
    private final long totalSuppressedDetections;
    private final long totalEvaluationFailures;
    private final long totalFailedPublishes;
    private final int evaluationQueueDepth;
    private final DetectionState currentState;

    public DetectionMetrics(
            int registeredRuleCount,
            int enabledRuleCount,
            long totalEvaluations,
            long totalDetections,
            long totalSuppressedDetections,
            long totalEvaluationFailures,
            long totalFailedPublishes,
            int evaluationQueueDepth,
            DetectionState currentState) {
        this.registeredRuleCount = registeredRuleCount;
        this.enabledRuleCount = enabledRuleCount;
        this.totalEvaluations = totalEvaluations;
        this.totalDetections = totalDetections;
        this.totalSuppressedDetections = totalSuppressedDetections;
        this.totalEvaluationFailures = totalEvaluationFailures;
        this.totalFailedPublishes = totalFailedPublishes;
        this.evaluationQueueDepth = evaluationQueueDepth;
        this.currentState = currentState;
    }

    public int getRegisteredRuleCount() {
        return registeredRuleCount;
    }

    public int getEnabledRuleCount() {
        return enabledRuleCount;
    }

    public long getTotalEvaluations() {
        return totalEvaluations;
    }

    public long getTotalDetections() {
        return totalDetections;
    }

    public long getTotalSuppressedDetections() {
        return totalSuppressedDetections;
    }

    public long getTotalEvaluationFailures() {
        return totalEvaluationFailures;
    }

    public long getTotalFailedPublishes() {
        return totalFailedPublishes;
    }

    public int getEvaluationQueueDepth() {
        return evaluationQueueDepth;
    }

    public DetectionState getCurrentState() {
        return currentState;
    }

    @Override
    public String toString() {
        return "DetectionMetrics{" +
                "registeredRuleCount=" + registeredRuleCount +
                ", enabledRuleCount=" + enabledRuleCount +
                ", totalEvaluations=" + totalEvaluations +
                ", totalDetections=" + totalDetections +
                ", totalSuppressedDetections=" + totalSuppressedDetections +
                ", totalEvaluationFailures=" + totalEvaluationFailures +
                ", totalFailedPublishes=" + totalFailedPublishes +
                ", evaluationQueueDepth=" + evaluationQueueDepth +
                ", currentState=" + currentState +
                '}';
    }
}
