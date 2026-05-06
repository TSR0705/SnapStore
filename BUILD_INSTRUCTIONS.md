# FileX Build Instructions

## Java Version Compatibility Issue

**Current System:** Java 25.0.2  
**Required for Build:** Java 21

### Problem

Gradle 8.12 and earlier versions do not fully support Java 25 due to Groovy/Kotlin compiler limitations. The build fails with:

```
Unsupported class file major version 69
```

### Solution Options

#### Option 1: Install Java 21 (Recommended)

1. Download and install **Java 21 LTS** from:
   - [Oracle JDK 21](https://www.oracle.com/java/technologies/downloads/#java21)
   - [Eclipse Temurin 21](https://adoptium.net/temurin/releases/?version=21)
   - [Amazon Corretto 21](https://aws.amazon.com/corretto/)

2. Set `JAVA_HOME` environment variable to Java 21 installation:
   ```powershell
   # Windows PowerShell (temporary - current session only)
   $env:JAVA_HOME = "C:\Path\To\Java21"
   $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
   
   # Verify
   java -version  # Should show Java 21
   ```

3. Run the build:
   ```powershell
   .\gradlew.bat clean build
   ```

#### Option 2: Use Gradle's Java Toolchain (If Java 21 is installed alongside Java 25)

If you have both Java 21 and Java 25 installed, you can tell Gradle to use Java 21 for compilation:

1. Edit `gradle.properties` and add:
   ```properties
   org.gradle.java.home=C:/Path/To/Java21
   ```

2. Run the build:
   ```powershell
   .\gradlew.bat clean build
   ```

#### Option 3: Use System Java 25 with Compatibility Mode (Not Recommended)

This approach compiles to Java 21 bytecode but may have limitations:

1. The current `build.gradle.kts` already sets:
   ```kotlin
   java {
       sourceCompatibility = JavaVersion.VERSION_21
       targetCompatibility = JavaVersion.VERSION_21
   }
   ```

2. However, Gradle's own internal components still fail with Java 25.

### Verification

Once Java 21 is configured, verify the build works:

```powershell
# Clean build
.\gradlew.bat clean build

# Run tests
.\gradlew.bat test

# Run the application
.\gradlew.bat run
```

### Expected Output

```
BUILD SUCCESSFUL in Xs
```

---

## Alternative: Manual Compilation (Without Gradle)

If you cannot install Java 21, you can compile manually with Java 25 targeting Java 21 bytecode:

```powershell
# Create output directory
mkdir -p build/classes

# Compile all Java files
javac -d build/classes `
      --release 21 `
      --module-path "path/to/javafx-sdk-21/lib" `
      --add-modules javafx.controls,javafx.fxml `
      -cp "lib/*" `
      (Get-ChildItem -Recurse -Filter *.java src/main/java).FullName

# Note: You'll need to manually download JavaFX SDK 21 and all dependencies
```

This is significantly more complex and not recommended for production builds.

---

## Recommended Action

**Install Java 21 LTS** — it's the officially supported version for this project and ensures full compatibility with all build tools and dependencies.
