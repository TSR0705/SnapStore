package com.filex.validation;

import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

/**
 * Standard SLF4J Log Markers for structured telemetry logging in FileX.
 */
public final class TruthMarkers {

    /** Marker for identifying structured engine event logs. */
    public static final Marker TRUTH = MarkerFactory.getMarker("TRUTH");

    private TruthMarkers() {
        // Prevent instantiation
    }
}
