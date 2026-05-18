# 🏗️ FileX Architecture Deep Dive

FileX is designed using clean architecture patterns with strict separation of concerns. This ensures that the system remains highly testable, easily maintainable, and completely decoupled.

---

## 1. Structural Layers

```
┌─────────────────────────────────────────────────────────────┐
│                     Presentation Layer                       │
│  (JavaFX Controllers, FXML Views, ViewManager)              │
│   Thin UI Controllers, no direct database or logic access   │
└────────────────────┬────────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────────┐
│                    Application Layer                         │
│  (AppContext, Bootstrap, EventBus, Services)                │
│   Dynamic orchestrators, thread executors, dynamic context  │
└────────────────────┬────────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────────┐
│                   Infrastructure Layer                       │
│  (DatabaseManager, ConfigManager, Repositories)             │
│   SQLite transaction engines, native OS filesystem binders  │
└─────────────────────────────────────────────────────────────┘
```

### Layer Responsibilities
*   **Presentation Layer:** Thin controllers linked to JavaFX FXML views. They interact solely with application layer services via asynchronously loaded models and publish zero raw SQL.
*   **Application Layer:** Coordinates lifecycle events, registers event handlers on the type-safe `EventBus`, and manages parallel execution queues.
*   **Infrastructure Layer:** Interfaces with OS-native filesystem listeners and persists security metadata using transaction templates inside SQLite.

---

## 2. Event-Driven Dispatch System (`EventBus.java`)

All components in FileX communicate asynchronously and safely through a central **Type-Safe Event Bus**. 

### Key Features
1.  **Type Safety:** Handlers subscribe to specific Java classes extending the base event framework.
2.  **MDC context propagation:** Automatically propagates thread-local diagnostic contexts (`MDC`) across asynchronous boundaries to preserve correlation IDs during deep analyses.
3.  **Decoupled Lifecycles:** Allows components like the `DetectionEngine` to process logs without needing to hold a direct pointer to the `AlertEngine`.

---

## 3. Dependency Injection (`AppContext.java`)

FileX completely rejects the **Singleton Anti-Pattern** (e.g., `getInstance()` methods).
Instead, all dependencies are initialized once during the [Bootstrap.java](file:///c:/Users/ACER/OneDrive/Desktop/FILE-X-REIMAGINE/src/main/java/com/filex/app/Bootstrap.java) phase and explicitly injected into constructors.

### Initialization Order:
1.  **Config Resolution:** Evaluates paths and debug environments.
2.  **Database Connection:** Connects to SQLite and executes migrations DDL.
3.  **Event Pipeline:** Launches the synchronous `EventBus`.
4.  **Engine Instantiation:** Starts monitoring, detection, and alerting processors.
5.  **UI Shell:** Bundles components inside `AppContext` and builds the JavaFX workspace.
