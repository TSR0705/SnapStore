package com.filex.validation;

import com.filex.detection.Confidence;
import com.filex.detection.MassDeletionDetectedEvent;
import com.filex.detection.Severity;
import com.filex.event.EventBus;
import com.filex.engine.RawFileCreatedEvent;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidationServiceTest {

    @Test
    void testValidationServiceTracksMonitoringAndDetectionEvents() {
        EventBus eventBus = new EventBus();
        ValidationService validationService = new ValidationService(eventBus);

        assertFalse(validationService.isStarted(), "ValidationService should not be started initially");

        validationService.start();
        assertTrue(validationService.isStarted(), "ValidationService should start correctly");

        eventBus.publish(new RawFileCreatedEvent(Path.of("/tmp/test.txt")));
        eventBus.publish(new MassDeletionDetectedEvent(
                "MASS_DELETE_RULE",
                Severity.HIGH,
                Confidence.HIGH,
                "Detected mass file deletion",
                Collections.singletonList(Path.of("/tmp/test.txt")),
                50,
                1000L
        ));

        ValidationService.ValidationMetrics metrics = validationService.getMetrics();
        assertEquals(1, metrics.monitoringEvents(), "Raw monitoring event count should have incremented");
        assertEquals(1, metrics.detectionEvents(), "Detection event count should have incremented");
        assertEquals(0, metrics.incidentCreatedEvents(), "Incident created event count should remain unchanged");
        assertTrue(metrics.validationServiceActive(), "ValidationService should report active state");

        validationService.stop();
        assertFalse(validationService.isStarted(), "ValidationService should stop cleanly");
    }
}
