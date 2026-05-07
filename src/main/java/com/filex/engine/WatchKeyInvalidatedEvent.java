package com.filex.engine;

import com.filex.event.AppEvent;
import com.filex.event.EventPriority;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Event indicating a WatchKey has been invalidated.
 *
 * <p>This occurs when:
 * <ul>
 *   <li>A monitored directory is deleted</li>
 *   <li>A drive is disconnected</li>
 *   <li>Permissions are lost</li>
 *   <li>The watch is explicitly cancelled</li>
 * </ul>
 *
 * <p>This is a HIGH priority event requiring immediate attention
 * to maintain monitoring coverage.
 */
public final class WatchKeyInvalidatedEvent extends AppEvent {

    private final Path path;
    private final String reason;

    public WatchKeyInvalidatedEvent(Path path, String reason) {
        super("MonitoringEngine", null, EventPriority.HIGH);
        this.path = Objects.requireNonNull(path, "path must not be null");
        this.reason = reason;
    }

    public Path path() {
        return path;
    }

    public String reason() {
        return reason;
    }

    @Override
    public String toString() {
        return "WatchKeyInvalidatedEvent{" +
                "path=" + path +
                ", reason='" + reason + '\'' +
                ", eventId='" + eventId() + '\'' +
                '}';
    }
}
