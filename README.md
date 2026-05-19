# <p align="center"><img src="pictures/filex-logo.png" alt="FileX Logo" width="100" style="border-radius: 20%;" /><br>FileX — Enterprise Endpoint Telemetry & Threat Detection Agent</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-orange.svg?style=for-the-badge&logo=java" alt="Java 21" />
  <img src="https://img.shields.io/badge/JavaFX-21-blue.svg?style=for-the-badge&logo=javafx" alt="JavaFX 21" />
  <img src="https://img.shields.io/badge/Database-SQLite-green.svg?style=for-the-badge&logo=sqlite" alt="SQLite" />
  <img src="https://img.shields.io/badge/Architecture-Clean-lightgrey.svg?style=for-the-badge" alt="Clean Architecture" />
  <img src="https://img.shields.io/badge/Status-SaaS--Production--Ready-brightgreen.svg?style=for-the-badge" alt="SaaS Ready" />
</p>

---

**FileX** is a production-grade, highly optimized local security agent designed to monitor host filesystem operations at the OS API layer, analyze telemetry in real-time using parallel heuristics, and persist security incidents inside a relational forensic archive.

Built on Java 21, JavaFX, and an asynchronous event-driven architecture, FileX stands out with zero global singleton abuse, clean constructor dependency injection, strict thread boundary isolation, and a premium SaaS-grade threat briefing workspace.

---

## 📚 Technical Documentation Index

Explore our comprehensive technical blueprints, developer setup guides, and contributor standards:

*   **[🏗️ Architecture Blueprint](docs/architecture.md)** — Clean design layers, type-safe event buses, and bootstrapping.
*   **[🛡️ Threat Heuristics Guide](docs/threat_detection.md)** — In-depth analysis of built-in security rules (Ransomware modify bursts, hidden dotfiles).
*   **[🛡️ Security & Trust Model](docs/security_model.md)** — Agent security context, trust boundaries, and OS access control.
*   **[🛠️ Developer Setup Guide](docs/development_setup.md)** — Local compile requirements, custom sandbox path resolutions, and IDE configurations.
*   **[🧪 Testing & Verification Guide](docs/testing_guide.md)** — Core unit tests, integration paths, and concurrency limits.
*   **[📊 Observability & Diagnostics](docs/observability.md)** — Structured key-value logging standards, MDC context propagation, and rolling file logs.
*   **[🔍 Operational Troubleshooting](docs/troubleshooting.md)** — Diagnosing directory permissions, UI lags, and SQLite WAL write-waits.
*   **[⚠️ Known Limitations](docs/known_limitations.md)** — In-memory backpressure, OS watch limits, and the product roadmap.

---

## 🏗️ Premium System Architecture

FileX utilizes a highly decoupled, reactive architectural pipeline. Below is the end-to-end telemetry workflow detailing how a physical filesystem event on the operating system transitions into a persistent threat record inside the operator's workspace UI:

```mermaid
graph TD
    %% Core Nodes
    OS["Windows Kernel (ReadDirectoryChangesW) / Linux inotify"]

    subgraph Monitoring Pipeline [Engine telemetry Layer]
        WS["java.nio.file.WatchService"]
        ME["MonitoringEngine (filex-watch-loop)"]
        RC["RawFileCreatedEvent"]
        RM["RawFileModifiedEvent"]
        RD["RawFileDeletedEvent"]
    end

    subgraph Event & Threat Pipeline [Reactive Core Layer]
        EB["EventBus (Type-safe dispatcher)"]
        DE["DetectionEngine (filex-detection-evaluator)"]
        AE["AlertEngine"]
        IC["IncidentCreatedEvent"]
    end

    subgraph Forensic Persistence [Infrastructure Storage Layer]
        IPS["IncidentPersistenceSubscriber"]
        DB[("SQLite WAL Database (filex.db)")]
    end

    subgraph Presentation & Operator UX [SaaS Operator Panel]
        IWC["InvestigationWorkspaceController"]
        UI["JavaFX SaaS Workspace UI"]
    end

    %% Pipeline Connections
    OS -->|OS Native Events| WS
    WS -->|Capture Event| ME
    ME -->|Normalize| RC
    ME -->|Normalize| RM
    ME -->|Normalize| RD

    RC & RM & RD -->|Publish| EB
    EB -->|Asynchronous Dispatch| DE

    DE -->|Evaluate Threat Rules| AE
    AE -->|Generate Incident| IC
    IC -->|Publish| EB

    EB -->|Capture Incident| IPS
    IPS -->|Transaction Write| DB

    EB -->|Live UI Refresh Callback| IWC
    IWC -->|Asynchronous SQL Query| DB
    IWC -->|Render Cards| UI

    %% Custom Styling
    style OS fill:#ff7675,stroke:#333,stroke-width:2px,color:#fff
    style DB fill:#55efc4,stroke:#333,stroke-width:2px,color:#000
    style UI fill:#0984e3,stroke:#333,stroke-width:2px,color:#fff
    style DE fill:#ffeaa7,stroke:#333,stroke-width:2px,color:#000
```

---

## ⚡ Concurrency & Thread Isolation Model

To maintain ultra-low overhead and guarantee that local monitoring never starves the operator dashboard or bottlenecks the operating system, FileX enforces a strict thread-boundary model:

*   **`filex-watch-loop` (Daemon):** Locks onto OS-native filesystem blocking hooks. It captures, normalizes, and exits the telemetry cycle within microseconds.
*   **`filex-detection-evaluator` (Executor Pool):** Dedicated worker thread pool handling parallel matching heuristics. Evaluation scales dynamically without ever blocking directory events.
*   **`JavaFX Application Thread`:** Purely handles workspace UI rendering and FXML lazy caching. Avoids lag during bulk telemetry processing.

```mermaid
sequenceDiagram
    autonumber
    actor Kernel as Windows Kernel
    participant Watcher as filex-watch-loop
    participant EBus as EventBus (Sync)
    participant Evaluator as filex-detection-evaluator
    participant Persister as SQLite Thread
    participant UI as JavaFX UI Thread

    Kernel->>Watcher: Write Event Generated
    activate Watcher
    Watcher->>EBus: Publish RawFileCreatedEvent
    deactivate Watcher

    activate EBus
    EBus->>Evaluator: Dispatch (Non-blocking queue)
    deactivate EBus

    activate Evaluator
    Note over Evaluator: Evaluate active rules in parallel
    Evaluator->>EBus: Publish IncidentCreatedEvent
    deactivate Evaluator

    activate EBus
    EBus->>Persister: Commit (IncidentPersistenceService)
    EBus->>UI: Platform.runLater (Notify UI)
    deactivate EBus

    activate Persister
    Persister->>Persister: Write SQLite WAL Transaction
    deactivate Persister

    activate UI
    UI->>UI: Refresh Workspace Dashboard
    deactivate UI
```

---

## 🛡️ Active Threat Protection Rules

FileX is equipped with five out-of-the-box, production-grade telemetry rules that run concurrently against system events:

| Threat Rule | Objective | Behavioral Heuristic Pattern | Severity |
| :--- | :--- | :--- | :--- |
| **`MassDeletionRule`** | Anti-Ransomware / Data Destruction | Checks if more than `N` files are deleted in a folder within a 5-second sliding window. | **HIGH** |
| **`RapidModificationRule`** | Mass Encryption / Data Locking | Detects quick, consecutive modifications to the same file or a high frequency of modifications across folders within a sliding temporal window. | **HIGH** |
| **`SuspiciousExtensionRenameRule`** | Masquerading / Double Extension | Flags attempts to disguise files or hide payloads (e.g., naming a file `invoice.pdf.exe`). | **MEDIUM** |
| **`HiddenFileCreationRule`** | Hidden Persistence Setup | Flags files created with OS hidden attributes or starting with leading dots in user spaces. | **MEDIUM** |
| **`SensitiveDirectoryActivityRule`** | System Path Compromise | Triggers immediate alerts upon write attempts in crucial system paths (e.g., `System32`, `etc`, `AppData`). | **CRITICAL** |

---

## 🚀 Installation & Execution

### Prerequisites
*   **Java Development Kit (JDK 21 or later)**
*   PowerShell or Bash terminal

### 1. Build the Project
```bash
./gradlew build
```

### 2. Run the Application

#### A. Standard Sandbox Mode (Monitors current sandbox folders)
```bash
./gradlew run
```

#### B. Enterprise Production Mode (Focuses monitoring on specific host directories)
Supply monitored directory target lists directly through JVM system flags:
```powershell
.\gradlew.bat "-Dfilex.monitor.paths=C:\Users\ACER\Downloads,C:\Users\ACER\Documents" run
```

---

## 🧩 Developer Extension Guide: Writing Custom Rules

FileX's dynamic architecture allows you to easily plug in new rules at runtime without modifying the core detection pipeline. Simply implement the `DetectionRule` interface and register it with the `DetectionEngine`.

### Custom Rule Template:

```java
package com.filex.detection.rules;

import com.filex.detection.*;

public final class MalwareNameRule implements DetectionRule {

    @Override
    public String name() {
        return "MalwareFileNameDetection";
    }

    @Override
    public String description() {
        return "Detects files containing malicious signatures in their filenames";
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public DetectionResult evaluate(MonitoringEvent event, DetectionContext context) {
        if (event.path() != null) {
            String filename = event.path().getFileName().toString().toLowerCase();
            if (filename.contains("malware") || filename.contains("ransomware")) {
                return DetectionResult.matched("File name matches dangerous threat signature.");
            }
        }
        return DetectionResult.notMatched();
    }
}
```

### Registration at Boot:
```java
// Register directly via the public API of the DetectionEngine
appContext.detectionEngine().registerRule(new MalwareNameRule());
```

---

## 🤝 Contributing, Support, & Code of Conduct

* **[🤝 Contribution Standards](CONTRIBUTING.md)** — Pull request guidelines, conventional commits, and branching frameworks.
* **[📜 Code of Conduct](CODE_OF_CONDUCT.md)** — Project empathy pledges and professional community guidelines.
* **[🏛️ Project Governance](GOVERNANCE.md)** — Decision-making policies, committees, and maintainership promotions.
* **[🛡️ Security Vulnerability Reporting](SECURITY.md)** — Confidential reporting guidelines and security SLAs.

---

## 📜 Licensing & Authors

*   **Author:** TSR0705
*   **License:** Proprietary — All Rights Reserved.
