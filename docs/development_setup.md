# 🛠️ FileX Developer Workspace & Environment Setup

Welcome to the contributor onboarding setup guide! This document outlines how to establish an isolated local developer sandbox, resolve JDK compatibility dependencies, and launch verification suites.

---

## 1. Local Workspace Prerequisites

Ensure you have the following system requirements:
* **Java Development Kit (JDK 21 LTS):** We officially recommend and support **JDK 21** (e.g., Eclipse Temurin, Oracle JDK, or Amazon Corretto).
* **Gradle Build Tool:** Automatically configured and bootstrapped via the provided `./gradlew` (or `.\gradlew.bat`) wrappers.
* **OS-specific permissions:** Administrative privileges are required on Windows to register recursive directory watchers on host file streams.

---

## 2. JDK Version Troubleshooting (e.g., Java 25 Compatibility)

### Problem
Gradle 8.12 and earlier versions do not fully support newer Java versions (such as Java 25) due to Groovy/Kotlin compiler limitations. Attempting to build with an unsupported JDK version will fail with:
```
Unsupported class file major version 69
```

### Solutions

#### Option A: Set Temporary Session Environment (Recommended)
Set the `JAVA_HOME` environment variable to point directly to your Java 21 installation in your active shell session:
```powershell
# Windows PowerShell (temporary - active session only)
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.10"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

# Verify
java -version  # Should show Java 21
```

#### Option B: Configure Local Gradle Properties
If you have multiple JDK versions installed on your machine, you can specify the target JDK home folder directly inside `gradle.properties`:
1. Open `gradle.properties` in the root folder.
2. Define the path to your Java 21 installation:
   ```properties
   org.gradle.java.home=C:/Program Files/Java/jdk-21.0.10
   ```

---

## 3. Compilation, Verification, & Execution

### A. Clean Build Assembly
Compile all Java source modules, run static audits, and package build archives:
```bash
./gradlew clean build
```

### B. Run Test Verification Suites
Execute all 262 unit and integration tests:
```bash
./gradlew test
```

### C. Launching the Application

#### Standard Sandbox Mode
Launches the JavaFX cyber threat operator panel and begins monitoring the local project space:
```bash
./gradlew run
```

#### Enterprise Host Watch Space
Provide specific host directories as target watch spaces using the `-Dfilex.monitor.paths` JVM argument (multiple directories can be comma-separated):
```powershell
.\gradlew.bat "-Dfilex.monitor.paths=C:\Users\ACER\Downloads,C:\Users\ACER\Documents" run
```

---

## 4. Recommended IDE Settings

### IntelliJ IDEA
1. Import the root folder as a standard **Gradle** project.
2. In **Project Structure** (`Ctrl+Alt+Shift+S`):
   * Set the **Project SDK** and **Project Language Level** to `SDK 21` / `Java 21`.
3. In **Settings** (`Ctrl+Alt+S`):
   * Navigate to **Build, Execution, Deployment** → **Compiler** → **Annotation Processors**.
   * Check **Enable annotation processing** (required to initialize SLF4J loggers and FXML runtime bindings).
