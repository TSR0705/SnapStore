package com.filex.investigation;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable investigation session context.
 *
 * <p>Maintains investigation state for replay continuity and analysis context.
 * Sessions are immutable snapshots that can be used to resume investigation
 * from a specific point.
 *
 * <p>Thread-safety: Immutable after construction.
 */
public final class InvestigationSession {

    private final String sessionId;
    private final Instant createdAt;
    private final InvestigationCriteria criteria;
    private final String focusIncidentId;
    private final String focusCorrelationId;
    private final Instant replayPosition;
    private final int currentPage;

    private InvestigationSession(Builder builder) {
        this.sessionId = builder.sessionId != null ? builder.sessionId : UUID.randomUUID().toString();
        this.createdAt = builder.createdAt != null ? builder.createdAt : Instant.now();
        this.criteria = builder.criteria;
        this.focusIncidentId = builder.focusIncidentId;
        this.focusCorrelationId = builder.focusCorrelationId;
        this.replayPosition = builder.replayPosition;
        this.currentPage = builder.currentPage;
    }

    // Getters
    public String getSessionId() { return sessionId; }
    public Instant getCreatedAt() { return createdAt; }
    public InvestigationCriteria getCriteria() { return criteria; }
    public String getFocusIncidentId() { return focusIncidentId; }
    public String getFocusCorrelationId() { return focusCorrelationId; }
    public Instant getReplayPosition() { return replayPosition; }
    public int getCurrentPage() { return currentPage; }

    public boolean hasCriteria() { return criteria != null; }
    public boolean hasFocusIncident() { return focusIncidentId != null; }
    public boolean hasFocusCorrelation() { return focusCorrelationId != null; }
    public boolean hasReplayPosition() { return replayPosition != null; }

    /**
     * Creates a new session with updated criteria.
     */
    public InvestigationSession withCriteria(InvestigationCriteria criteria) {
        return builder()
                .sessionId(this.sessionId)
                .createdAt(this.createdAt)
                .criteria(criteria)
                .focusIncidentId(this.focusIncidentId)
                .focusCorrelationId(this.focusCorrelationId)
                .replayPosition(this.replayPosition)
                .currentPage(this.currentPage)
                .build();
    }

    /**
     * Creates a new session with updated focus incident.
     */
    public InvestigationSession withFocusIncident(String incidentId) {
        return builder()
                .sessionId(this.sessionId)
                .createdAt(this.createdAt)
                .criteria(this.criteria)
                .focusIncidentId(incidentId)
                .focusCorrelationId(this.focusCorrelationId)
                .replayPosition(this.replayPosition)
                .currentPage(this.currentPage)
                .build();
    }

    /**
     * Creates a new session with updated replay position.
     */
    public InvestigationSession withReplayPosition(Instant replayPosition) {
        return builder()
                .sessionId(this.sessionId)
                .createdAt(this.createdAt)
                .criteria(this.criteria)
                .focusIncidentId(this.focusIncidentId)
                .focusCorrelationId(this.focusCorrelationId)
                .replayPosition(replayPosition)
                .currentPage(this.currentPage)
                .build();
    }

    /**
     * Creates a new session with updated page number.
     */
    public InvestigationSession withPage(int pageNumber) {
        return builder()
                .sessionId(this.sessionId)
                .createdAt(this.createdAt)
                .criteria(this.criteria)
                .focusIncidentId(this.focusIncidentId)
                .focusCorrelationId(this.focusCorrelationId)
                .replayPosition(this.replayPosition)
                .currentPage(pageNumber)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        return "InvestigationSession{" +
                "sessionId='" + sessionId + '\'' +
                ", createdAt=" + createdAt +
                ", hasCriteria=" + hasCriteria() +
                ", focusIncidentId='" + focusIncidentId + '\'' +
                ", currentPage=" + currentPage +
                '}';
    }

    public static final class Builder {
        private String sessionId;
        private Instant createdAt;
        private InvestigationCriteria criteria;
        private String focusIncidentId;
        private String focusCorrelationId;
        private Instant replayPosition;
        private int currentPage;

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder criteria(InvestigationCriteria criteria) {
            this.criteria = criteria;
            return this;
        }

        public Builder focusIncidentId(String focusIncidentId) {
            this.focusIncidentId = focusIncidentId;
            return this;
        }

        public Builder focusCorrelationId(String focusCorrelationId) {
            this.focusCorrelationId = focusCorrelationId;
            return this;
        }

        public Builder replayPosition(Instant replayPosition) {
            this.replayPosition = replayPosition;
            return this;
        }

        public Builder currentPage(int currentPage) {
            this.currentPage = currentPage;
            return this;
        }

        public InvestigationSession build() {
            return new InvestigationSession(this);
        }
    }
}
