# 🛠️ FileX Developer Workspace Setup Guide

Welcome to the contributor onboarding setup guide! This document outlines how to establish an isolated local developer sandbox, resolve JDK dependencies, and launch verification suites.

---

## 1. Local Workspace Prerequisites

Ensure you have the following system requirements:
* **Java Development Kit (JDK 21 or later):** We use Temurin distributions for testing.
* **Gradle Build Tool:** Configured automatically via `./gradlew` wrappers.
* **OS-specific permissions:** Administrative privileges are required on Windows to watch configuration folders recursive streams.

---

## 2. Compilation and Execution

### Clean Build Assembly
Compile all Java source modules and assemble artifact archives:
```bash
./gradlew clean build
```

### Starting the Application

#### A. Interactive Demo watch Mode
Monitor the current local project sandbox (default target):
```bash
./gradlew run
```

#### B. Enterprise Production Watch Space
Provide specific system directories as target watch spaces using the `-Dfilex.monitor.paths` flag (multiple paths can be comma-separated):
```powershell
.\gradlew.bat "-Dfilex.monitor.paths=C:\Users\ACER\Downloads,C:\Users\ACER\Documents" run
```

---

## 3. Recommended IDE Settings

* **IntelliJ IDEA:**
  * Import as standard Gradle project.
  * Set **Project SDK** to Java 21.
  * Enable **Annotation Processing** under compiler settings to support SLF4J logging and FXML injectors.
