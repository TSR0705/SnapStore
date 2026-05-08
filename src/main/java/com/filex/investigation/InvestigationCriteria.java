package com.filex.investigation;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable criteria for forensic investigation queries.
 *
 * <p>Provides composable filtering for incident exploration, timeline analysis,
 * and evidence traversal. All criteria are optional and combine with AND logic.
 *
 * <p>Thread-safety: Immutable after construction.
 */
public final class InvestigationCriteria {

    private final Set<String> severities;
    private final Set<String> confidences;
    private final Set<String> statuses;
    private final Set<String> ruleNames;
    private final Set<String> eventTypes;
    private final String pathPattern;
    private final String correlationId;
    private final Instant startTime;
    private final Instant endTime;
    private final Integer maxResults;

    private InvestigationCriteria(Builder builder) {
        this.severities = builder.severities != null ? Set.copyOf(builder.severities) : Set.of();
        this.confidences = builder.confidences != null ? Set.copyOf(builder.confidences) : Set.of();
        this.statuses = builder.statuses != null ? Set.copyOf(builder.statuses) : Set.of();
        this.ruleNames = builder.ruleNames != null ? Set.copyOf(builder.ruleNames) : Set.of();
        this.eventTypes = builder.eventTypes != null ? Set.copyOf(builder.eventTypes) : Set.of();
        this.pathPattern = builder.pathPattern;
        this.correlationId = builder.correlationId;
        this.startTime = builder.startTime;
        this.endTime = builder.endTime;
        this.maxResults = builder.maxResults;
    }

    // Getters
    public Set<String> getSeverities() { return severities; }
    public Set<String> getConfidences() { return confidences; }
    public Set<String> getStatuses() { return statuses; }
    public Set<String> getRuleNames() { return ruleNames; }
    public Set<String> getEventTypes() { return eventTypes; }
    public String getPathPattern() { return pathPattern; }
    public String getCorrelationId() { return correlationId; }
    public Instant getStartTime() { return startTime; }
    public Instant getEndTime() { return endTime; }
    public Integer getMaxResults() { return maxResults; }

    public boolean hasSeverityFilter() { return !severities.isEmpty(); }
    public boolean hasConfidenceFilter() { return !confidences.isEmpty(); }
    public boolean hasStatusFilter() { return !statuses.isEmpty(); }
    public boolean hasRuleNameFilter() { return !ruleNames.isEmpty(); }
    public boolean hasEventTypeFilter() { return !eventTypes.isEmpty(); }
    public boolean hasPathFilter() { return pathPattern != null; }
    public boolean hasCorrelationFilter() { return correlationId != null; }
    public boolean hasTimeRangeFilter() { return startTime != null && endTime != null; }
    public boolean hasMaxResultsLimit() { return maxResults != null; }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        return "InvestigationCriteria{" +
                "severities=" + severities +
                ", statuses=" + statuses +
                ", timeRange=" + (hasTimeRangeFilter() ? startTime + " to " + endTime : "none") +
                ", maxResults=" + maxResults +
                '}';
    }

    public static final class Builder {
        private Set<String> severities;
        private Set<String> confidences;
        private Set<String> statuses;
        private Set<String> ruleNames;
        private Set<String> eventTypes;
        private String pathPattern;
        private String correlationId;
        private Instant startTime;
        private Instant endTime;
        private Integer maxResults;

        public Builder severities(Set<String> severities) {
            this.severities = severities;
            return this;
        }

        public Builder confidences(Set<String> confidences) {
            this.confidences = confidences;
            return this;
        }

        public Builder statuses(Set<String> statuses) {
            this.statuses = statuses;
            return this;
        }

        public Builder ruleNames(Set<String> ruleNames) {
            this.ruleNames = ruleNames;
            return this;
        }

        public Builder eventTypes(Set<String> eventTypes) {
            this.eventTypes = eventTypes;
            return this;
        }

        public Builder pathPattern(String pathPattern) {
            this.pathPattern = pathPattern;
            return this;
        }

        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public Builder timeRange(Instant startTime, Instant endTime) {
            this.startTime = Objects.requireNonNull(startTime, "startTime must not be null");
            this.endTime = Objects.requireNonNull(endTime, "endTime must not be null");
            if (endTime.isBefore(startTime)) {
                throw new IllegalArgumentException("endTime must be after startTime");
            }
            return this;
        }

        public Builder maxResults(Integer maxResults) {
            if (maxResults != null && maxResults <= 0) {
                throw new IllegalArgumentException("maxResults must be positive");
            }
            this.maxResults = maxResults;
            return this;
        }

        public InvestigationCriteria build() {
            return new InvestigationCriteria(this);
        }
    }
}
