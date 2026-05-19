package com.filex.app;

/**
 * Standard JavaFX Launcher entry point.
 *
 * <p>This class does not extend javafx.application.Application. This bypasses the JDK's strict
 * JavaFX runtime availability checks at boot, allowing the application to execute directly from a
 * standard JVM classpath on any operating system.
 */
public final class Main {

  private Main() {
    // Non-instantiable entry point launcher
  }

  public static void main(String[] args) {
    FileXApplication.main(args);
  }
}
