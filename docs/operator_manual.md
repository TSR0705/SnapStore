# 💻 Operator & Incident Investigation Workspace Manual

The FileX Investigation Workspace provides security teams with a slate modern, reactive interface to review, analyze, and manage active incident alerts.

---

## 1. Main Navigation Shell

The UI is built with JavaFX and is designed with clean CSS themes:
*   **Sidebar Control:** Toggle between the Incident Workspace, Threat Profile setups, and System Performance dashboards.
*   **Active Counter:** View dynamic badges reflecting active, critical, and pending incident counts.
*   **Live Status Indicator:** Real-time feedback bar at the bottom showing the monitoring engines' health (e.g. `Watching 445 directories`).

---

## 2. Investigation Workspace Dashboard

```
┌─────────────────────────────────────────────────────────────┐
│                       Top Search Panel                      │
│   Search by Filename, Rule Name, or Severity filter         │
├──────────────────────────────┬──────────────────────────────┤
│                              │                              │
│      Incidents Master List   │      Detailed Evidence Card  │
│      ─────────────────────   │      ──────────────────────  │
│                              │                              │
│   [!] High - Ransomware      │   ► Chronological Timeline   │
│   [!] Medium - Masquerade    │   ► Evidence File Paths      │
│   [!] Critical - System32    │   ► Incident Status Toggles  │
│                              │                              │
└──────────────────────────────┴──────────────────────────────┘
```

### A. Master Alerts List (Left Panel)
*   Displays a list of all live and resolved threats in reverse chronological order.
*   Color-coded threat tags:
    *   🔴 **Critical:** Immediate system path modifications.
    *   🟠 **High:** Anti-ransomware deletion rate triggers.
    *   🟡 **Medium:** Masqueraded extensions and hidden files.
    *   🔵 **Low:** Minor anomaly detections.

### B. Forensic Timeline & Evidence Chain (Right Panel)
*   **Forensic Timeline:** A step-by-step history showing how the event developed (e.g. `File created ➔ Modified ➔ High-rate write observed ➔ Alert triggered`).
*   **Evidence List:** Clickable links displaying exact absolute system paths of the suspicious files.
*   **Investigation Action Bar:** Set the incident state to `RESOLVED` or flag as `FALSE_POSITIVE` to keep clean records.

---

## 3. Triggering Live Test Telemetry

To test the operator workspace on a live Windows system:
1.  Navigate into a monitored path folder.
2.  Open PowerShell and drop a double-extension file signature:
    ```powershell
    New-Item -Path ".\invoice.pdf.exe" -ItemType "file"
    ```
3.  The file creation event is captured instantly at the OS layer.
4.  The dashboard list updates live, rendering a **Medium-Severity Masquerading Anomaly Card** automatically!
