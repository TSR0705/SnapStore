package com.filex.alert;

import com.filex.event.AppEvent;

/**
 * Event published when an existing incident is updated.
 *
 * <p>Updates include:
 *
 * <ul>
 *   <li>Severity escalation
 *   <li>Status changes
 *   <li>Additional detections linked
 *   <li>Evidence updates
 * </ul>
 */
public final class IncidentUpdatedEvent extends AppEvent {

  private final Incident incident;
  private final String updateReason;

  public IncidentUpdatedEvent(Incident incident, String updateReason) {
    super("AlertEngine");
    this.incident = incident;
    this.updateReason = updateReason;
  }

  public Incident getIncident() {
    return incident;
  }

  public String getUpdateReason() {
    return updateReason;
  }

  @Override
  public String toString() {
    return "IncidentUpdatedEvent{"
        + "incidentId='"
        + incident.getIncidentId()
        + '\''
        + ", reason='"
        + updateReason
        + '\''
        + ", severity="
        + incident.getSeverity()
        + ", eventId='"
        + eventId()
        + '\''
        + '}';
  }
}
