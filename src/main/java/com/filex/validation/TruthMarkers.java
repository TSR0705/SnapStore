package com.filex.validation;

import java.util.Collection;
import java.util.Iterator;

import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

/**
 * SLF4J marker constants and structured key=value formatting helpers
 * for FileX truth-validation logging.
 *
 * <p>All truth-validation log statements carry the {@link #TRUTH} marker so
 * they can be routed to a dedicated appender (e.g. {@code logs/truth.log})
 * via a Logback {@code EvaluatorFilter} without polluting the main app log.
 *
 * <p>Helper methods produce flat {@code key=value} fragments designed to
 * grep cleanly with tools like {@code grep}/{@code awk}/log shippers.
 * Values are sanitized: whitespace, newlines, and ASCII control characters
 * are replaced with {@code '_'} so a single log line never spans multiple
 * physical lines and never contains structural whitespace inside a value.
 *
 * <p>This class is a final utility holder with a private constructor;
 * all methods are pure functions and therefore inherently thread-safe.
 */
public final class TruthMarkers {

    /** Shared SLF4J marker for every truth-validation log statement. */
    public static final Marker TRUTH = MarkerFactory.getMarker("TRUTH");

    private TruthMarkers() {
        // utility class - no instances
    }

    /**
     * Formats a single {@code key=value} pair with value sanitization.
     *
     * @param key   structured-log key (assumed safe, used verbatim)
     * @param value any object; {@code null} renders as {@code "null"}
     * @return {@code key + "=" + sanitized(value)}
     */
    public static String kv(String key, Object value) {
        return key + "=" + sanitize(value);
    }

    /**
     * Formats a collection of values as {@code key=[v1,v2,v3]} with each
     * element sanitized identically to {@link #kv(String, Object)}.
     *
     * @param key    structured-log key (assumed safe, used verbatim)
     * @param values collection of values; may be {@code null} or empty
     * @return {@code key + "=[" + joined + "]"}
     */
    public static String kvList(String key, Collection<?> values) {
        StringBuilder sb = new StringBuilder();
        sb.append(key).append("=[");
        if (values != null && !values.isEmpty()) {
            Iterator<?> it = values.iterator();
            boolean first = true;
            while (it.hasNext()) {
                if (!first) {
                    sb.append(',');
                }
                sb.append(sanitize(it.next()));
                first = false;
            }
        }
        sb.append(']');
        return sb.toString();
    }

    /**
     * Replaces spaces, newlines, and ASCII control characters with {@code '_'}.
     * {@code null} renders as the literal string {@code "null"}.
     */
    private static String sanitize(Object value) {
        if (value == null) {
            return "null";
        }
        String s = value.toString();
        int n = s.length();
        StringBuilder out = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t' || c < 0x20 || c == 0x7F) {
                out.append('_');
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
