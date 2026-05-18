# 📚 FileX Technical Documentation Hub

Welcome to the central technical documentation hub for **FileX**. This index guides contributors, security engineers, and operators through the detailed blueprints, setup manuals, and runtime structures of the FileX agent.

---

## 🏗️ 1. Architecture & Design Blueprints
* **[🏗️ System Architecture deep-dive](architecture.md)** — Explains clean layers, constructor dependency injection, and decoupling.
* **[🛡️ Threat Detection Heuristics](threat_detection.md)** — Analysis of built-in behavioral rules (Mass Deletion, Rapid Modifications).
* **[🛡️ Agent Security Model](security_model.md)** — In-depth threat modeling, trust boundaries, and OS access control.

---

## 🛠️ 2. Developer & Contributor Center
* **[🛠️ Developer setup & Builds](development_setup.md)** — Gradle compilers, sandbox paths, and IDE imports.
* **[🧪 Verification & Testing standards](testing_guide.md)** — Unit tests, integration suites, and temporal concurrency testing.
* **[📊 Observability & diagnostics](observability.md)** — Structured SLF4J log lines, MDC thread context tracing, and Logback.

---

## 💻 3. Operational & Forensic Runbooks
* **[💻 Operator & Investigation Manual](operator_manual.md)** — Renders, incident lists, evidence, and simulators.
* **[🔍 Troubleshooting Runbook](troubleshooting.md)** — Fixes for file access warnings, UI lags, and SQLite locks.
* **[⚠️ Known Limitations](known_limitations.md)** — OS file watcher boundaries, events backpressure, and roadmap.
