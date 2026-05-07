package com.filex.engine;

import com.filex.event.AppEvent;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Event indicating the monitoring engine has started successfully.
 *
 * <p>Published after all initial watch registrations are complete.
 */
public final class MonitoringStartedEvent extends AppEvent {

    private final List<String> monitoredPaths;

    public MonitoringStartedEvent(List<String> monitoredPaths) {
        super("MonitoringEngine");
        this.monitoredPaths = Collections.unmodifiableList(
                Objects.requireNonNull(monitoredPaths, "monitoredPaths must not be null"));
    }

    public List<String> monitoredPaths() {
        return monitoredPaths;
    }

    @Override
    public String toString() {
        return "MonitoringStartedEvent{" +
                "monitoredPaths=" + monitoredPaths +
                ", eventId='" + eventId() + '\'' +
                '}';
    }
}
