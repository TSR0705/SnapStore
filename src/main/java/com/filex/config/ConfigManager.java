package com.filex.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves and constructs the application {@link AppConfig}.
 *
 * <p>Resolution strategy (in priority order):
 *
 * <ol>
 *   <li>Environment variable {@code FILEX_HOME} — explicit override
 *   <li>OS-specific user data directory (AppData on Windows, ~/.local/share on Linux/macOS)
 *   <li>User home fallback
 * </ol>
 *
 * <p>This class is stateless after construction. It creates required directories eagerly so the
 * rest of the bootstrap can assume they exist.
 */
public final class ConfigManager {

  // Static initializer to set log directory BEFORE any logger is created
  static {
    try {
      Path appHome = resolveAppHome();
      Path logsDir = appHome.resolve("logs");

      // Ensure logs directory exists before setting property
      if (!Files.exists(logsDir)) {
        Files.createDirectories(logsDir);
      }

      System.setProperty("FILEX_LOG_DIR", logsDir.toAbsolutePath().toString());
    } catch (Exception e) {
      // Fallback to relative logs directory if resolution fails
      System.setProperty("FILEX_LOG_DIR", "logs");
      System.err.println(
          "Warning: Could not resolve log directory, using relative path: " + e.getMessage());
    }
  }

  private static final Logger log = LoggerFactory.getLogger(ConfigManager.class);

  private static final String ENV_FILEX_HOME = "FILEX_HOME";
  private static final String APP_FOLDER_NAME = "FileX";

  private ConfigManager() {
    // Non-instantiable utility — use static factory
  }

  /**
   * Resolves the application configuration and ensures all required directories exist on the
   * filesystem.
   *
   * @return fully resolved, immutable {@link AppConfig}
   * @throws ConfigurationException if directories cannot be created
   */
  public static AppConfig resolve() {
    log.info("Resolving application configuration...");

    Path appHome = resolveAppHome();
    Path logsDir = appHome.resolve("logs");
    Path dataDir = appHome.resolve("data");
    Path configDir = appHome.resolve("config");

    boolean debugMode = resolveDebugMode();
    Path databaseFile = dataDir.resolve(AppConfig.DB_FILENAME);

    ensureDirectory(appHome);
    ensureDirectory(logsDir);
    ensureDirectory(dataDir);
    ensureDirectory(configDir);

    AppConfig config =
        new AppConfig(
            appHome,
            logsDir,
            dataDir,
            configDir,
            databaseFile,
            AppConfig.APP_NAME,
            AppConfig.APP_VERSION,
            debugMode);

    log.info(config.summary());
    return config;
  }

  // -------------------------------------------------------------------------
  // Private helpers
  // -------------------------------------------------------------------------

  private static Path resolveAppHome() {
    // 1. Explicit environment override
    String envHome = System.getenv(ENV_FILEX_HOME);
    if (envHome != null && !envHome.isBlank()) {
      log.info("Using FILEX_HOME from environment: {}", envHome);
      return Paths.get(envHome);
    }

    // 2. OS-specific user data directory
    String os = System.getProperty("os.name", "").toLowerCase();
    if (os.contains("win")) {
      String appData = System.getenv("APPDATA");
      if (appData != null && !appData.isBlank()) {
        return Paths.get(appData, APP_FOLDER_NAME);
      }
    } else if (os.contains("mac")) {
      return Paths.get(
          System.getProperty("user.home"), "Library", "Application Support", APP_FOLDER_NAME);
    } else {
      // Linux / Unix
      String xdgData = System.getenv("XDG_DATA_HOME");
      if (xdgData != null && !xdgData.isBlank()) {
        return Paths.get(xdgData, APP_FOLDER_NAME);
      }
      return Paths.get(System.getProperty("user.home"), ".local", "share", APP_FOLDER_NAME);
    }

    // 3. Fallback: user home
    log.warn("Could not determine OS-specific data directory. Falling back to user.home.");
    return Paths.get(System.getProperty("user.home"), APP_FOLDER_NAME);
  }

  private static boolean resolveDebugMode() {
    String debugEnv = System.getenv("FILEX_DEBUG");
    if (debugEnv != null) {
      return "true".equalsIgnoreCase(debugEnv.trim());
    }
    return "true".equalsIgnoreCase(System.getProperty("filex.debug", "false"));
  }

  private static void ensureDirectory(Path path) {
    try {
      if (!Files.exists(path)) {
        Files.createDirectories(path);
        log.debug("Created directory: {}", path);
      }
    } catch (Exception e) {
      throw new ConfigurationException("Failed to create required directory: " + path, e);
    }
  }
}
