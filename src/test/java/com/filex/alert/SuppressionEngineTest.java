package com.filex.alert;

import com.filex.detection.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SuppressionEngine.
 */
class SuppressionEngineTest {

    @TempDir
    Path tempDir;

    private SuppressionEngine suppressionEngine;

    @BeforeEach
    void setUp() {
        suppressionEngine = new SuppressionEngine(1000); // 1 second cooldown
    }

    @Test
    void testFirstAlertNotSuppressed() {
        MassDeletionDetectedEvent detection = new MassDeletionDetectedEvent(
                "MassDeletionRule",
                Severity.HIGH,
                Confidence.HIGH,
                "Mass deletion",
                List.of(tempDir.resolve("file1.txt")),
                60,
                10000
        );

        boolean suppressed = suppressionEngine.shouldSuppress(detection);
        assertFalse(suppressed, "First alert should not be suppressed");
    }

    @Test
    void testDuplicateAlertSuppressed() {
        MassDeletionDetectedEvent detection = new MassDeletionDetectedEvent(
                "MassDeletionRule",
                Severity.HIGH,
                Confidence.HIGH,
                "Mass deletion",
                List.of(tempDir.resolve("file1.txt")),
                60,
                10000
        );

        // First alert
        boolean suppressed1 = suppressionEngine.shouldSuppress(detection);
        assertFalse(suppressed1, "First alert should not be suppressed");

        // Immediate duplicate
        boolean suppressed2 = suppressionEngine.shouldSuppress(detection);
        assertTrue(suppressed2, "Duplicate alert should be suppressed");
    }

    @Test
    void testSuppressionExpiresAfterCooldown() throws Exception {
        MassDeletionDetectedEvent detection = new MassDeletionDetectedEvent(
                "MassDeletionRule",
                Severity.HIGH,
                Confidence.HIGH,
                "Mass deletion",
                List.of(tempDir.resolve("file1.txt")),
                60,
                10000
        );

        // First alert
        boolean suppressed1 = suppressionEngine.shouldSuppress(detection);
        assertFalse(suppressed1);

        // Wait for cooldown to expire
        Thread.sleep(1100);

        // Should not be suppressed after cooldown
        boolean suppressed2 = suppressionEngine.shouldSuppress(detection);
        assertFalse(suppressed2, "Alert should not be suppressed after cooldown");
    }

    @Test
    void testDifferentSeverityNotSuppressed() {
        Path file = tempDir.resolve("file1.txt");

        MassDeletionDetectedEvent detection1 = new MassDeletionDetectedEvent(
                "MassDeletionRule",
                Severity.MEDIUM,
                Confidence.MEDIUM,
                "Some deletions",
                List.of(file),
                30,
                10000
        );

        MassDeletionDetectedEvent detection2 = new MassDeletionDetectedEvent(
                "MassDeletionRule",
                Severity.HIGH,
                Confidence.HIGH,
                "Mass deletion",
                List.of(file),
                60,
                10000
        );

        suppressionEngine.shouldSuppress(detection1);
        
        // Different severity should not be suppressed (escalation)
        boolean suppressed = suppressionEngine.shouldSuppress(detection2);
        assertFalse(suppressed, "Different severity should not be suppressed");
    }

    @Test
    void testDifferentPathNotSuppressed() {
        MassDeletionDetectedEvent detection1 = new MassDeletionDetectedEvent(
                "MassDeletionRule",
                Severity.HIGH,
                Confidence.HIGH,
                "Mass deletion",
                List.of(tempDir.resolve("file1.txt")),
                60,
                10000
        );

        MassDeletionDetectedEvent detection2 = new MassDeletionDetectedEvent(
                "MassDeletionRule",
                Severity.HIGH,
                Confidence.HIGH,
                "Mass deletion",
                List.of(tempDir.resolve("file2.txt")),
                60,
                10000
        );

        suppressionEngine.shouldSuppress(detection1);
        
        // Different path should not be suppressed
        boolean suppressed = suppressionEngine.shouldSuppress(detection2);
        assertFalse(suppressed, "Different path should not be suppressed");
    }

    @Test
    void testCleanupExpiredSuppressions() throws Exception {
        // Create multiple suppressions
        for (int i = 0; i < 10; i++) {
            MassDeletionDetectedEvent detection = new MassDeletionDetectedEvent(
                    "MassDeletionRule",
                    Severity.HIGH,
                    Confidence.HIGH,
                    "Mass deletion " + i,
                    List.of(tempDir.resolve("file" + i + ".txt")),
                    60,
                    10000
            );
            suppressionEngine.shouldSuppress(detection);
        }

        assertTrue(suppressionEngine.getSuppressionCacheSize() > 0);

        // Wait for expiration
        Thread.sleep(1100);

        int removed = suppressionEngine.cleanupExpiredSuppressions();
        assertTrue(removed > 0, "Should remove expired suppressions");
    }

    @Test
    void testClear() {
        MassDeletionDetectedEvent detection = new MassDeletionDetectedEvent(
                "MassDeletionRule",
                Severity.HIGH,
                Confidence.HIGH,
                "Mass deletion",
                List.of(tempDir.resolve("file1.txt")),
                60,
                10000
        );

        suppressionEngine.shouldSuppress(detection);
        assertTrue(suppressionEngine.getSuppressionCacheSize() > 0);

        suppressionEngine.clear();
        assertEquals(0, suppressionEngine.getSuppressionCacheSize());
    }
}
