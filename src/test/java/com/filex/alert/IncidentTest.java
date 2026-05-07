package com.filex.alert;

import com.filex.detection.Confidence;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Incident model.
 */
class IncidentTest {

    @Test
    void testIncidentBuilder() {
        Instant now = Instant.now();
        
        Incident incident = Incident.builder()
                .incidentId("incident-123")
                .severity(IncidentSeverity.HIGH)
                .confidence(Confidence.HIGH)
                .status(IncidentStatus.OPEN)
                .title("Mass Deletion Detected")
                .description("60 files deleted in 10 seconds")
                .createdAt(now)
                .updatedAt(now)
                .lastSeenAt(now)
                .addLinkedDetectionId("detection-1")
                .correlationId("corr-123")
                .evidenceSummary(Map.of("fileCount", 60))
                .escalationLevel(0)
                .detectionCount(1)
                .build();

        assertEquals("incident-123", incident.getIncidentId());
        assertEquals(IncidentSeverity.HIGH, incident.getSeverity());
        assertEquals(Confidence.HIGH, incident.getConfidence());
        assertEquals(IncidentStatus.OPEN, incident.getStatus());
        assertEquals("Mass Deletion Detected", incident.getTitle());
        assertEquals(1, incident.getLinkedDetectionIds().size());
        assertEquals(1, incident.getDetectionCount());
    }

    @Test
    void testIncidentImmutability() {
        Incident incident = Incident.builder()
                .incidentId("incident-123")
                .severity(IncidentSeverity.HIGH)
                .confidence(Confidence.HIGH)
                .status(IncidentStatus.OPEN)
                .title("Test")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .lastSeenAt(Instant.now())
                .build();

        List<String> detectionIds = incident.getLinkedDetectionIds();
        assertThrows(UnsupportedOperationException.class, () -> 
                detectionIds.add("new-detection"));

        Map<String, Object> evidence = incident.getEvidenceSummary();
        assertThrows(UnsupportedOperationException.class, () -> 
                evidence.put("key", "value"));
    }

    @Test
    void testToBuilder() {
        Instant now = Instant.now();
        
        Incident original = Incident.builder()
                .incidentId("incident-123")
                .severity(IncidentSeverity.MEDIUM)
                .confidence(Confidence.MEDIUM)
                .status(IncidentStatus.OPEN)
                .title("Original Title")
                .createdAt(now)
                .updatedAt(now)
                .lastSeenAt(now)
                .detectionCount(1)
                .build();

        Incident updated = original.toBuilder()
                .severity(IncidentSeverity.HIGH)
                .detectionCount(2)
                .build();

        assertEquals("incident-123", updated.getIncidentId());
        assertEquals(IncidentSeverity.HIGH, updated.getSeverity());
        assertEquals(2, updated.getDetectionCount());
        assertEquals("Original Title", updated.getTitle());
    }

    @Test
    void testRequiredFields() {
        assertThrows(NullPointerException.class, () -> 
                Incident.builder().build());

        assertThrows(NullPointerException.class, () -> 
                Incident.builder()
                        .incidentId("id")
                        .build());
    }
}
