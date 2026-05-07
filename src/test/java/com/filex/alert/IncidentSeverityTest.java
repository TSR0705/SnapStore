package com.filex.alert;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for IncidentSeverity enum.
 */
class IncidentSeverityTest {

    @Test
    void testSeverityLevels() {
        assertEquals(1, IncidentSeverity.LOW.getLevel());
        assertEquals(2, IncidentSeverity.MEDIUM.getLevel());
        assertEquals(3, IncidentSeverity.HIGH.getLevel());
        assertEquals(4, IncidentSeverity.CRITICAL.getLevel());
    }

    @Test
    void testMaxSeverity() {
        assertEquals(IncidentSeverity.HIGH, 
                IncidentSeverity.max(IncidentSeverity.LOW, IncidentSeverity.HIGH));
        
        assertEquals(IncidentSeverity.CRITICAL, 
                IncidentSeverity.max(IncidentSeverity.MEDIUM, IncidentSeverity.CRITICAL));
        
        assertEquals(IncidentSeverity.MEDIUM, 
                IncidentSeverity.max(IncidentSeverity.LOW, IncidentSeverity.MEDIUM));
    }

    @Test
    void testEscalation() {
        assertEquals(IncidentSeverity.MEDIUM, IncidentSeverity.LOW.escalate());
        assertEquals(IncidentSeverity.HIGH, IncidentSeverity.MEDIUM.escalate());
        assertEquals(IncidentSeverity.CRITICAL, IncidentSeverity.HIGH.escalate());
        assertEquals(IncidentSeverity.CRITICAL, IncidentSeverity.CRITICAL.escalate());
    }
}
