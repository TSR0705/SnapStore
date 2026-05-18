package com.filex.validation;

import com.filex.alert.IncidentCreatedEvent;
import com.filex.alert.IncidentUpdatedEvent;
import com.filex.detection.HiddenFileDetectedEvent;
import com.filex.detection.MassDeletionDetectedEvent;
import com.filex.detection.RapidModificationDetectedEvent;
import com.filex.detection.SensitiveDirectoryAccessDetectedEvent;
import com.filex.detection.SuspiciousRenameDetectedEvent;
import com.filex.engine.MonitoringOverflowEvent;
import com.filex.engine.RawDirectoryCreatedEvent;
import com.filex.engine.RawFileCreatedEvent;
import com.filex.engine.RawFileDeletedEvent;
import com.filex.engine.RawFileModifiedEvent;
import com.filex.engine.WatchKeyInvalidatedEvent;
import com.filex.event.EventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Observes the FileX event pipeline in validation mode.
 *
 * <p>Validation mode is an instrumentation-only runtime mode that
 * records the truth of event flow across monitoring, detection, and
 * alerting without changing pipeline behavior.
 */
public final class ValidationService {

    private static final Logger log = LoggerFactory.getLogger(ValidationService.class);

    private final EventBus eventBus;

    private final AtomicLong monitoringEvents = new AtomicLong(0);
    private final AtomicLong monitoringOverflowEvents = new AtomicLong(0);
    private final AtomicLong invalidatedWatchKeys = new AtomicLong(0);
    private final AtomicLong detectionEvents = new AtomicLong(0);
    private final AtomicLong incidentCreatedEvents = new AtomicLong(0);
    private final AtomicLong incidentUpdatedEvents = new AtomicLong(0);

    private volatile boolean started = false;

    private final Consumer<RawFileCreatedEvent> rawFileCreatedHandler = event -> monitoringEvents.incrementAndGet();
    private final Consumer<RawFileModifiedEvent> rawFileModifiedHandler = event -> monitoringEvents.incrementAndGet();
    private final Consumer<RawFileDeletedEvent> rawFileDeletedHandler = event -> monitoringEvents.incrementAndGet();
    private final Consumer<RawDirectoryCreatedEvent> rawDirectoryCreatedHandler = event -> monitoringEvents.incrementAndGet();
    private final Consumer<MonitoringOverflowEvent> monitoringOverflowHandler = event -> monitoringOverflowEvents.incrementAndGet();
    private final Consumer<WatchKeyInvalidatedEvent> watchKeyInvalidatedHandler = event -> invalidatedWatchKeys.incrementAndGet();

    private final Consumer<MassDeletionDetectedEvent> massDeletionHandler = event -> detectionEvents.incrementAndGet();
    private final Consumer<RapidModificationDetectedEvent> rapidModificationHandler = event -> detectionEvents.incrementAndGet();
    private final Consumer<SuspiciousRenameDetectedEvent> suspiciousRenameHandler = event -> detectionEvents.incrementAndGet();
    private final Consumer<HiddenFileDetectedEvent> hiddenFileHandler = event -> detectionEvents.incrementAndGet();
    private final Consumer<SensitiveDirectoryAccessDetectedEvent> sensitiveDirectoryHandler = event -> detectionEvents.incrementAndGet();

    private final Consumer<IncidentCreatedEvent> incidentCreatedHandler = event -> incidentCreatedEvents.incrementAndGet();
    private final Consumer<IncidentUpdatedEvent> incidentUpdatedHandler = event -> incidentUpdatedEvents.incrementAndGet();

    public ValidationService(EventBus eventBus) {
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus must not be null");
        log.info("ValidationService created");
    }

    public synchronized void start() {
        if (started) {
            return;
        }

        log.info("Validation mode enabled. ValidationService starting...");
        eventBus.subscribe(RawFileCreatedEvent.class, rawFileCreatedHandler);
        eventBus.subscribe(RawFileModifiedEvent.class, rawFileModifiedHandler);
        eventBus.subscribe(RawFileDeletedEvent.class, rawFileDeletedHandler);
        eventBus.subscribe(RawDirectoryCreatedEvent.class, rawDirectoryCreatedHandler);
        eventBus.subscribe(MonitoringOverflowEvent.class, monitoringOverflowHandler);
        eventBus.subscribe(WatchKeyInvalidatedEvent.class, watchKeyInvalidatedHandler);

        eventBus.subscribe(MassDeletionDetectedEvent.class, massDeletionHandler);
        eventBus.subscribe(RapidModificationDetectedEvent.class, rapidModificationHandler);
        eventBus.subscribe(SuspiciousRenameDetectedEvent.class, suspiciousRenameHandler);
        eventBus.subscribe(HiddenFileDetectedEvent.class, hiddenFileHandler);
        eventBus.subscribe(SensitiveDirectoryAccessDetectedEvent.class, sensitiveDirectoryHandler);

        eventBus.subscribe(IncidentCreatedEvent.class, incidentCreatedHandler);
        eventBus.subscribe(IncidentUpdatedEvent.class, incidentUpdatedHandler);

        started = true;
        log.info("ValidationService started successfully.");
    }

    public synchronized void stop() {
        if (!started) {
            return;
        }

        log.info("ValidationService stopping...");

        eventBus.unsubscribe(RawFileCreatedEvent.class, rawFileCreatedHandler);
        eventBus.unsubscribe(RawFileModifiedEvent.class, rawFileModifiedHandler);
        eventBus.unsubscribe(RawFileDeletedEvent.class, rawFileDeletedHandler);
        eventBus.unsubscribe(RawDirectoryCreatedEvent.class, rawDirectoryCreatedHandler);
        eventBus.unsubscribe(MonitoringOverflowEvent.class, monitoringOverflowHandler);
        eventBus.unsubscribe(WatchKeyInvalidatedEvent.class, watchKeyInvalidatedHandler);

        eventBus.unsubscribe(MassDeletionDetectedEvent.class, massDeletionHandler);
        eventBus.unsubscribe(RapidModificationDetectedEvent.class, rapidModificationHandler);
        eventBus.unsubscribe(SuspiciousRenameDetectedEvent.class, suspiciousRenameHandler);
        eventBus.unsubscribe(HiddenFileDetectedEvent.class, hiddenFileHandler);
        eventBus.unsubscribe(SensitiveDirectoryAccessDetectedEvent.class, sensitiveDirectoryHandler);

        eventBus.unsubscribe(IncidentCreatedEvent.class, incidentCreatedHandler);
        eventBus.unsubscribe(IncidentUpdatedEvent.class, incidentUpdatedHandler);

        started = false;
        log.info("ValidationService stopped. Summary: {}", getMetrics());
    }

    public boolean isStarted() {
        return started;
    }

    public ValidationMetrics getMetrics() {
        return new ValidationMetrics(
                monitoringEvents.get(),
                monitoringOverflowEvents.get(),
                invalidatedWatchKeys.get(),
                detectionEvents.get(),
                incidentCreatedEvents.get(),
                incidentUpdatedEvents.get(),
                started
        );
    }

    public record ValidationMetrics(
            long monitoringEvents,
            long monitoringOverflowEvents,
            long invalidatedWatchKeys,
            long detectionEvents,
            long incidentCreatedEvents,
            long incidentUpdatedEvents,
            boolean validationServiceActive
    ) {
    }
}
