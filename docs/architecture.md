# 🏗️ Detailed FileX Architectural Framework

This document complements the root **[ARCHITECTURE.md](../ARCHITECTURE.md)**, focusing specifically on deep structural layers, dynamic constructor dependency injection, and internal pipeline mechanisms.

---

## 1. Structural Design & Presentation Cleanliness

FileX operates under strict domain segregation to guarantee absolute component decoupling. This prevents database leakage into presentation and isolates business rules from display frameworks.

```
┌─────────────────────────────────────────────────────────────┐
│                     Presentation Layer                       │
│  (JavaFX Controllers, FXML Views, ViewManager)              │
│  Thin, purely declarative layout binders. Publishes zero    │
│  SQL queries and handles zero detection heuristics.         │
└────────────────────┬────────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────────┐
│                    Application Layer                         │
│  (AppContext, Bootstrap, EventBus, Services)                │
│  The dynamic runtime orchestrator. Decouples workflows      │
│  via asynchronous multi-producer single-consumer buffers.  │
└────────────────────┬────────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────────┐
│                   Infrastructure Layer                       │
│  (DatabaseManager, ConfigManager, Repositories)             │
│  OS-native WatchService bindings and SQLite WAL storage.   │
└─────────────────────────────────────────────────────────────┘
```

---

## 2. Dependency Injection and The AppContext Container

To maintain clean object lifecycles, **FileX explicitly rejects all forms of static singleton pattern abuse** (e.g. `getInstance()` methods). Instead, instances are created once by `com.filex.app.Bootstrap` and wrapped inside an immutable context container:

```java
public final class AppContext {
    private final AppConfig config;
    private final DatabaseManager databaseManager;
    private final EventBus eventBus;
    private final MonitoringEngine monitoringEngine;
    private final DetectionEngine detectionEngine;
    private final AlertEngine alertEngine;
    private final WorkspaceService workspaceService;
    // Constructor-based composing
}
```

This strict layout enables:
* **Frictionless Unit Testing:** Dependencies can be mocked effortlessly.
* **Deterministic Shutdowns:** Engines are torn down in precise reverse order to prevent active file descriptor leaks.
* **Config Flexibility:** The application can run concurrently in distinct isolated sandboxes without cross-polluting global variables.
