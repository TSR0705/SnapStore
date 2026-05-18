# 🧪 FileX Verification & Testing Standards

To maintain absolute detection accuracy and runtime safety, all code commits must pass our verification pipeline before merging.

---

## 1. Test Architecture

FileX maintains three distinct layers of testing:
* **Unit Tests:** Target isolated stateful and stateless rules (e.g. `RapidModificationRuleTest`, `MassDeletionRuleTest`).
* **Integration Tests:** Trace the event pipeline path from `MonitoringEngine` event captures through the `EventBus` into SQL persistence transactions.
* **Concurrency stress Tests:** Validate that bulk event queues operate without deadlocks on SQLite transactional locking boundaries.

---

## 2. Executing Verification Suites

### Run All Tests
```bash
./gradlew test
```

### Run Specific Heuristic Unit Test
```bash
./gradlew test --tests "com.filex.detection.rules.RapidModificationRuleTest"
```

---

## 3. Writing Stateful Rule Tests

When authoring custom rules, you must mock temporal filesystem timelines inside your unit test. Leverage our rule-testing suite to mock events separated by exact durations to verify stateful sliding windows (e.g., simulating 5 file writes in less than 2 seconds).
