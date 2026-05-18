# 🔍 Operator Troubleshooting & Diagnostic Runbook

This runbook outlines solutions for common runtime challenges encountered by operators when deploying or developing FileX.

---

## 1. File Watcher Initialization Issues

### 🔴 Symptom: `AccessDeniedException` on Startup
On Windows platforms, when trying to monitor system configuration directories or standard User paths, the application fails to start with:
```log
java.nio.file.AccessDeniedException: C:\Users\ACER\Documents\My Pictures
```
* **Cause:** Windows protects specific legacy symlink junctions (like `My Pictures` or `My Videos` inside standard Documents) from recursive reads even for administrative users.
* **Solution:** FileX automatically intercepts these warnings and successfully skips corrupted subdirectories, registering the remaining clean parent directories without failing. Ensure you are launching the console terminal using **Run as Administrator** to allow base scans.

---

## 2. JavaFX UI Thread Lag & Blank Screens

### 🔴 Symptom: The Sidebar Cards Freeze During Heavy Writes
* **Cause:** Heavily nested directory simulations (e.g., Ransomware or Mass Deletion) are executing tasks directly on the main thread rather than offloading to backend executors, blocking JavaFX redraw frames.
* **Solution:** All FileX simulators are designed to spawn background execution pools. If you write custom simulator buttons in FXML, wrap their action hooks inside a separate `new Thread(() -> { ... }).start();` execution block to guarantee UI thread fluid redraws.

---

## 3. Database Locks

### 🔴 Symptom: `org.sqlite.SQLiteException: [SQLITE_BUSY] The database file is locked`
* **Cause:** Multiple parallel thread writes trying to insert forensic timelines simultaneously.
* **Solution:** FileX uses a centralized transactional database manager that executes queries inside synchronized thread queues. If you are writing custom persistent modules, always route write queries through the `DatabaseManager` and avoid launching raw JDBC connections directly from parallel threads.
