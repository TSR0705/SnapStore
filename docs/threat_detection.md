# 🛡️ FileX Behavioral Threat Detection & Heuristic Engines

FileX protects endpoints by monitoring real-time filesystem operations and evaluating them against specialized behavioral rules that detect suspicious activities, ransomware patterns, and masquerading attempts.

---

## 1. Built-In Heuristic Rules

### A. Ransomware Mass Deletion (`MassDeletionRule`)
*   **Objective:** Prevent rapid data wiping or ransomware cleaning behaviors.
*   **Mechanism:** Evaluates deletion events inside a 5-second temporal sliding window. If more than 20 files are deleted within the same folder space, an alert is immediately generated.
*   **Impact:** **HIGH** severity threat notification.

### B. High-Frequency Modification (`RapidModificationRule`)
*   **Objective:** Detect background bulk-encryption attacks (ransomware locking).
*   **Mechanism:** Watches write events on files. If a single file or a group of files in a directory receives more than 5 modifications per second, it triggers a warning.
*   **Impact:** **HIGH** severity threat notification.

### C. Masquerading Re-extension (`SuspiciousExtensionRenameRule`)
*   **Objective:** Catch malicious double extensions designed to trick operators into executing malware.
*   **Mechanism:** Parses newly created or renamed files. Matches against double extensions (e.g., `*.pdf.exe`, `*.txt.bat`, `*.jpg.vbs`).
*   **Impact:** **MEDIUM** severity threat notification.

### D. Stealth Persistence (`HiddenFileCreationRule`)
*   **Objective:** Detect hidden scripts or payloads dropped by external droppers.
*   **Mechanism:** Inspects physical file properties via `Files.getAttribute(path, "dos:hidden")` on Windows or checking for leading dot prefixes (`.`) on Linux.
*   **Impact:** **MEDIUM** severity threat notification.

### E. Privilege Escalation Path Write (`SensitiveDirectoryActivityRule`)
*   **Objective:** Block malware attempts to modify OS libraries or write startup scripts.
*   **Mechanism:** Hardcoded system folders (like `C:\Windows\System32`, `/etc`, or critical user registry areas) are registered. Any write activity in these spaces triggers an instant critical alarm.
*   **Impact:** **CRITICAL** severity threat notification.

---

## 2. Dynamic Rule Evaluation Loop

```
[OS Watcher File Event]
        │
        ▼
[Normalizer (Raw Events)]
        │
        ▼
[EventBus Asynchronous Dispatch]
        │
        ▼
[Detection Queue (EVALUATION_THREADS)]
        │
        ├── Rule 1: MassDeletion?  ──► Match? ──► High Alert
        ├── Rule 2: RapidMod?      ──► Match? ──► High Alert
        ├── Rule 3: HiddenFile?    ──► Match? ──► Medium Alert
        └── ...
```

The matching loop runs concurrently inside a dedicated executor thread pool (`filex-detection-evaluator`). This prevents heavy computational rule processing from ever lagging the filesystem listener.
