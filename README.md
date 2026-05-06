# FileX — Endpoint Telemetry & Suspicious File Activity Monitor

**Phase 1A: Foundation Complete**

FileX is a production-grade endpoint monitoring system designed to detect and track suspicious file activity in real-time. This repository contains the foundational architecture for a scalable, event-driven desktop agent.

---

## 🏗️ Architecture Overview

FileX follows **clean architecture** principles with strict separation of concerns:

```
┌─────────────────────────────────────────────────────────────┐
│                     Presentation Layer                       │
│  (JavaFX Controllers, FXML Views, ViewManager)              │
└────────────────────┬────────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────────┐
│                    Application Layer                         │
│  (AppContext, Bootstrap, Event Bus, Services)               │
└────────────────────┬────────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────────┐
│                   Infrastructure Layer                       │
│  (DatabaseManager, ConfigManager, Repositories)             │
└─────────────────────────────────────────────────────────────┘
```

### Key Design Decisions

- **No Singleton Abuse**: Dependencies are passed explicitly through constructors
- **Cached View Navigation**: FXML is loaded once and reused — no repeated parsing
- **Event-Driven Foundation**: Decoupled components communicate via EventBus
- **Fail-Fast Bootstrap**: Application terminates immediately if initialization fails
- **Explicit Lifecycle Management**: Resources are acquired and released in strict order

---

## 🚀 Phase 1A: What's Implemented

### ✅ Core Infrastructure

- [x] **Configuration System** — OS-aware directory resolution (AppData on Windows, XDG on Linux)
- [x] **SQLite Database** — WAL mode, foreign key enforcement, schema versioning
- [x] **Logging** — SLF4J + Logback with rolling file appenders
- [x] **Event Bus** — Synchronous, type-safe event dispatch
- [x] **AppContext** — Root dependency container (no service locator pattern)

### ✅ JavaFX Application Shell

- [x] **Bootstrap Lifecycle** — Ordered initialization with fail-fast semantics
- [x] **ViewManager** — Lazy-loaded, cached view navigation
- [x] **Controller Factory** — Constructor-based dependency injection
- [x] **Main Layout** — Sidebar navigation + content area
- [x] **Overview View** — Placeholder landing page

### ✅ Engineering Standards

- [x] **Gradle Build** — Java 21 toolchain, JavaFX plugin, dependency management
- [x] **Test Foundation** — JUnit 5 with smoke tests for bootstrap and config
- [x] **Clean Package Structure** — Logical separation by layer and concern
- [x] **Production-Grade .gitignore** — Excludes build artifacts, logs, and databases

---

## 📦 Tech Stack

| Component       | Technology                  |
|-----------------|-----------------------------|
| Language        | Java 21                     |
| UI Framework    | JavaFX 21                   |
| Build Tool      | Gradle 8.x                  |
| Database        | SQLite (JDBC)               |
| Logging         | SLF4J + Logback             |
| Testing         | JUnit 5                     |

---

## 🛠️ Build & Run

### Prerequisites

- **Java 21** (JDK 21 or later)
- **Gradle** (wrapper included — no manual install required)

### Build

```bash
./gradlew build
```

### Run

```bash
./gradlew run
```

### Run Tests

```bash
./gradlew test
```

---

## 📂 Project Structure

```
FileX/
├── src/main/java/com/filex/
│   ├── app/              # Bootstrap, AppContext, FileXApplication
│   ├── config/           # ConfigManager, AppConfig
│   ├── controller/       # JavaFX controllers (thin, no business logic)
│   ├── database/         # DatabaseManager, schema bootstrap
│   ├── event/            # EventBus, AppEvent hierarchy
│   ├── ui/               # ViewManager, ViewId, ControllerFactory
│   ├── detection/        # (Phase 2+) File activity detection engine
│   ├── engine/           # (Phase 2+) Monitoring engine
│   ├── model/            # (Phase 2+) Domain entities
│   ├── repository/       # (Phase 2+) Data access layer
│   ├── service/          # (Phase 2+) Business logic services
│   └── util/             # (Future) Shared utilities
│
├── src/main/resources/
│   ├── fxml/             # JavaFX view definitions
│   ├── css/              # Application stylesheets
│   └── logback.xml       # Logging configuration
│
├── src/test/java/        # Unit and integration tests
├── logs/                 # Application logs (gitignored)
├── data/                 # SQLite database (gitignored)
└── build.gradle          # Gradle build configuration
```

---

## 🗂️ Database Schema (Phase 1A)

### `schema_version`
Tracks applied schema migrations for future versioning.

| Column      | Type    | Description                  |
|-------------|---------|------------------------------|
| id          | INTEGER | Primary key                  |
| version     | TEXT    | Schema version identifier    |
| applied_at  | TEXT    | ISO 8601 timestamp           |
| description | TEXT    | Migration description        |

### `app_startup_log`
Audit log of application launches.

| Column       | Type    | Description                  |
|--------------|---------|------------------------------|
| id           | INTEGER | Primary key                  |
| started_at   | TEXT    | ISO 8601 timestamp           |
| app_version  | TEXT    | Application version          |
| hostname     | TEXT    | Machine hostname             |
| os_name      | TEXT    | Operating system name        |
| java_version | TEXT    | Java runtime version         |

---

## 🔧 Configuration

### Environment Variables

| Variable       | Description                          | Default                          |
|----------------|--------------------------------------|----------------------------------|
| `FILEX_HOME`   | Override application home directory  | OS-specific (AppData, XDG, etc.) |
| `FILEX_DEBUG`  | Enable debug mode                    | `false`                          |

### Directory Resolution (Priority Order)

1. **`FILEX_HOME` environment variable** (explicit override)
2. **OS-specific user data directory**:
   - Windows: `%APPDATA%\FileX`
   - macOS: `~/Library/Application Support/FileX`
   - Linux: `~/.local/share/FileX`
3. **Fallback**: `~/FileX`

---

## 🧪 Testing Strategy

### Phase 1A Tests

- **Bootstrap Smoke Test**: Validates full initialization sequence
- **Config Resolution Test**: Verifies directory creation and path resolution
- **Database Initialization Test**: Confirms schema creation and connection lifecycle

### Future Phases

- Unit tests for detection algorithms
- Integration tests for monitoring engine
- UI tests for JavaFX controllers
- Performance tests for event throughput

---

## 🚧 Roadmap

### Phase 1B: Monitoring Engine Foundation
- [ ] File system watcher integration
- [ ] Event capture pipeline
- [ ] Baseline activity profiling

### Phase 2: Detection Logic
- [ ] Suspicious file pattern detection
- [ ] Behavioral anomaly scoring
- [ ] Alert generation and persistence

### Phase 3: Reporting & Analytics
- [ ] Dashboard with real-time metrics
- [ ] Historical activity reports
- [ ] Export to CSV/JSON

### Phase 4: Sync & Backend Integration
- [ ] REST API client for central server
- [ ] Encrypted telemetry upload
- [ ] Remote configuration management

---

## 📜 License

Proprietary — All Rights Reserved

---

## 👤 Author

**TSR0705**  
GitHub: [TSR0705](https://github.com/TSR0705)
