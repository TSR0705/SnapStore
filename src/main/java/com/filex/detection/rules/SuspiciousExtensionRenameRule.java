package com.filex.detection.rules;

import com.filex.detection.*;
import com.filex.engine.RawFileCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Detects suspicious file extension changes.
 *
 * <p>Triggers when files are created with suspicious extensions
 * commonly associated with ransomware encryption.
 *
 * <p>Suspicious extensions include:
 * .encrypted, .locked, .crypted, .crypto, .crypt, .enc, .locky, .cerber, etc.
 */
public final class SuspiciousExtensionRenameRule implements DetectionRule {

    private static final Logger log = LoggerFactory.getLogger(SuspiciousExtensionRenameRule.class);

    private static final Set<String> SUSPICIOUS_EXTENSIONS = Set.of(
            ".encrypted", ".locked", ".crypted", ".crypto", ".crypt",
            ".enc", ".locky", ".cerber", ".wannacry", ".petya",
            ".ryuk", ".maze", ".revil", ".sodinokibi"
    );

    private volatile boolean enabled = true;

    @Override
    public String name() {
        return "SuspiciousExtensionRenameRule";
    }

    @Override
    public String description() {
        return "Detects suspicious file extension changes associated with ransomware encryption";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public DetectionResult evaluate(DetectionContext context) {
        // Only evaluate on CREATE events (renames appear as CREATE)
        if (!(context.currentEvent() instanceof RawFileCreatedEvent)) {
            return DetectionResult.notDetected();
        }

        RawFileCreatedEvent event = (RawFileCreatedEvent) context.currentEvent();
        Path path = event.path();
        String filename = path.getFileName().toString().toLowerCase();

        // Check for suspicious extensions
        for (String suspiciousExt : SUSPICIOUS_EXTENSIONS) {
            if (filename.endsWith(suspiciousExt)) {
                log.warn("Suspicious file extension detected: {} (extension: {})",
                        path, suspiciousExt);

                Map<String, Object> contextData = new HashMap<>();
                contextData.put("suspiciousExtension", suspiciousExt);
                contextData.put("filename", filename);

                return DetectionResult.detected(
                        Severity.HIGH,
                        Confidence.HIGH,
                        String.format("Suspicious file extension detected: %s", suspiciousExt),
                        contextData
                );
            }
        }

        return DetectionResult.notDetected();
    }
}
