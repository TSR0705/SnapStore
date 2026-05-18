# 🗺️ FileX Product Roadmap

This roadmap details the future architectural milestones, feature additions, and security capabilities planned for **FileX**.

---

## 🟢 Phase 1: End-to-End Simulation & UI Overhaul (Completed)
* [x] **Event Monitoring Pipeline:** Connect the OS NIO watcher loop safely to the JavaFX Application Thread.
* [x] **Virtual Threat Simulators:** Add dynamic, multi-threaded Ransomware, Mass Deletion, Key Injectors, and Extension rename buttons.
* [x] **Premium SaaS Visual Dashboard:** Build colorful badge lists, hide database UUID primary keys, and render high-fidelity forensic briefings with remediation playbooks.

---

## 🟡 Phase 2: Core Hardening & Scalability (Active)
* [ ] **Backpressure Queue Limits:** Enforce maximum capacity thresholds on the evaluation queue to prevent JVM out-of-memory errors during extreme bulk write storms (e.g. system upgrades).
* [ ] **Index Optimizations:** Create database composite indexes on the `incident_evidence` table for fast timestamp range forensic scans.
* [ ] **Config Encryption:** Secure the sandboxed database credential paths.

---

## 🔵 Phase 3: Platform Expansion (Planned)
* [ ] **Linux Native Watched Bindings (`inotify`):** Introduce JNI bindings for Linux kernels, avoiding JVM polling overhead.
* [ ] **Remote Telemetry Exporter:** Expose a secure Prometheus metric exporter endpoint (`/metrics`) to aggregate threat indicators into enterprise SIEMs (Splunk, Datadog).
