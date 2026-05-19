# 📜 FileX Version Changelog

All notable changes to the **FileX** project are documented in this file, adhering strictly to the **Keep a Changelog** standard.

---

## [1.2.0] — 2026-05-18

### Added
* **Interactive Threat Simulation Studio:** 5 high-fidelity cyber attack simulator buttons (Ransomware encryption, mass file deletion, hidden payload setup, sensitive credential write, extension rename) on the Overview screen.
* **Directory Selector:** Custom native folder target chooser allowing users to actively select target folders to register dynamically with the monitoring thread.

### Changed
* **FAANG-Grade UI Overhaul:** Complete redesign of the Investigation Workspace shell and Evidence Detail pane.
* **Badges & Playbooks:** Integrated rounded severity pills, telemetry metadata grids, and high-visibility dashed cyber-red incident containment playbooks.
* **Clean Cell Mapping:** Swapped out raw hex database primary key UUIDs for formatted threat title mapping in list views.

---

## [1.1.0] — 2026-05-13

### Fixed
* **FXML Wiring:** Resolved FXML mapping issues in controller wireups.
* **Event Pipeline Stability:** Fixed transaction write synchronization on SQLite locks, resolving WAL file creation blockages on startup.
