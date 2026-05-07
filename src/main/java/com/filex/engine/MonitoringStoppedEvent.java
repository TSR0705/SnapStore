package com.filex.engine;

import com.filex.event.AppEvent;

/**
 * Event indicating the monitoring engine has stopped.
 *
 * <p>Published after all watch keys are invalidated and
 * monitoring threads are shutdown.
 */
public final class MonitoringStoppedEvent extends AppEvent {

    private final String reason;

    public MonitoringStoppedEvent(String reason) {
        super("MonitoringEngine");
        this.reason = reason;
    }

    public String reason() {
        return reason;
    }

    @Override
    public String toString() {
        return "MonitoringStoppedEvent{" +
                "reason='" + reason + '\'' +
                ", eventId='" + eventId() + '\'' +
                '}';
    }
}
