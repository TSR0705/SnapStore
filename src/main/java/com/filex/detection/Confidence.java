package com.filex.detection;

/**
 * Confidence levels for detection results.
 *
 * <p>Confidence indicates how certain the detection engine is
 * that the detected behavior is truly suspicious.
 */
public enum Confidence {

    /**
     * Low confidence - behavior may be suspicious but has high false-positive risk.
     */
    LOW,

    /**
     * Medium confidence - behavior is likely suspicious.
     */
    MEDIUM,

    /**
     * High confidence - behavior is very likely suspicious.
     */
    HIGH
}
