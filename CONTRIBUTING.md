# 🤝 Contributing to FileX

Thank you for your interest in contributing to **FileX**! We want to make contributing to this project as frictionless and rewarding as possible.

By participating in this project, you agree to abide by our **[Code of Conduct](CODE_OF_CONDUCT.md)**.

---

## 1. Ground Rules & Expectations

* **Zero Singleton Abuse:** We enforce clean dependency injection. Do not introduce static global `getInstance()` methods. Compose dependencies in `AppContext` and inject them via constructors.
* **Thread Boundary Separation:** All background I/O or evaluation loops must be offloaded to isolated daemon threads. UI elements must be mutated strictly on the `JavaFX Application Thread` via `Platform.runLater()`.
* **Test Coverage:** All bug fixes or new features must be accompanied by comprehensive unit or integration tests.

---

## 2. Our Development Workflow

We use a standard GitHub flow. Follow these steps to submit a contribution:

1. **Fork the Repository:** Create your own fork of `TSR0705/SnapStore`.
2. **Create a Feature Branch:** Branch out from `main` using descriptive names:
   ```bash
   git checkout -b feat/add-network-watcher
   ```
3. **Commit Conventionally:** We enforce the **Conventional Commits** standard:
   * `feat(detection): add regex-based matching rules`
   * `fix(persistence): resolve db lock under WAL write stress`
   * `docs(readme): update installation prereqs`
4. **Run Verification Locally:** Before pushing, ensure all builds and tests pass cleanly:
   ```bash
   ./gradlew clean test
   ```
5. **Open a Pull Request:** Detail your changes using our PR template.

---

## 3. Creating a Custom Telemetry Rule

To implement a new threat detection heuristic:
1. Create a class implementing the `DetectionRule` interface inside `com.filex.detection.rules`.
2. Implement your logic in `evaluate(MonitoringEvent event, DetectionContext context)`.
3. Register the rule within `com.filex.detection.DetectionEngine#initializeRules()`.

---

## 4. Getting Help

If you run into issues during local setup:
* Check out the **[Developer Setup Guide](docs/development_setup.md)**.
* Join the discussion or file an issue in our **[Issue Tracker](https://github.com/TSR0705/SnapStore/issues)**.
