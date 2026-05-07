package com.filex.alert;

import com.filex.event.AppEvent;

/**
 * Event published when a new incident is created.
 *
 * <p>This event signals that a new security incident has been identified
 * and is ready for investigation or response.
 */
public final class IncidentCreatedEvent extends AppEvent {

    private final Incident incident;

    public IncidentCreatedEvent(Incident incident) {
        super("AlertEngine");
        this.incident = incident;
    }

    public Incident getIncident() {
        return incident;
    }

    @Override
    public String toString() {
        return "IncidentCreatedEvent{" +
                "incidentId='" + incident.getIncidentId() + '\'' +
                ", severity=" + incident.getSeverity() +
                ", title='" + incident.getTitle() + '\'' +
                ", eventId='" + eventId() + '\'' +
                '}';
    }
}
