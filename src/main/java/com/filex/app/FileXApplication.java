package com.filex.app;

import com.filex.event.ApplicationShutdownEvent;
import com.filex.event.ApplicationStartedEvent;
import com.filex.ui.ViewId;
import java.net.URL;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JavaFX application entry point.
 *
 * <p>Lifecycle:
 *
 * <ol>
 *   <li>{@link #init()} — runs on JavaFX launcher thread before UI starts. Executes the full {@link
 *       Bootstrap} sequence.
 *   <li>{@link #start(Stage)} — runs on JavaFX Application Thread. Loads the main layout FXML and
 *       displays the primary stage.
 *   <li>{@link #stop()} — runs on JavaFX Application Thread during shutdown. Publishes shutdown
 *       event and releases all resources.
 * </ol>
 *
 * <p>This class owns the {@link AppContext} for the application's lifetime.
 */
public final class FileXApplication extends Application {

  private static final Logger log = LoggerFactory.getLogger(FileXApplication.class);

  private AppContext appContext;

  /**
   * JavaFX lifecycle: initialization phase. Runs BEFORE the JavaFX Application Thread starts.
   *
   * <p>We perform the full bootstrap here so that any failures occur before the UI is shown. This
   * prevents showing a broken window.
   */
  @Override
  public void init() throws Exception {
    log.info("JavaFX init() phase starting...");
    appContext = Bootstrap.initialize();
    log.info("JavaFX init() phase complete.");
  }

  /**
   * JavaFX lifecycle: start phase. Runs on the JavaFX Application Thread.
   *
   * <p>Loads the main layout FXML, configures the primary stage, and makes the window visible.
   */
  @Override
  public void start(Stage primaryStage) throws Exception {
    log.info("JavaFX start() phase starting...");

    // Load the main layout FXML
    URL fxmlUrl = getClass().getResource(ViewId.MAIN_LAYOUT.fxmlPath());
    if (fxmlUrl == null) {
      throw new IllegalStateException(
          "MainLayout.fxml not found at: " + ViewId.MAIN_LAYOUT.fxmlPath());
    }

    FXMLLoader loader = new FXMLLoader(fxmlUrl);
    loader.setControllerFactory(controllerClass -> createController(controllerClass, appContext));

    Parent root = loader.load();

    // Configure the primary stage
    Scene scene = new Scene(root, 1100, 700);
    primaryStage.setScene(scene);
    primaryStage.setTitle("FileX - Endpoint Telemetry Monitor");
    primaryStage.setMinWidth(900);
    primaryStage.setMinHeight(600);

    // Show the stage
    primaryStage.show();
    log.info("Primary stage displayed.");

    // Start the operational runtime (starts engines, demo mode, etc.)
    appContext.runtimeManager().start();
    log.info("RuntimeManager started.");

    // Publish application started event
    appContext.eventBus().publish(new ApplicationStartedEvent());
    log.info("ApplicationStartedEvent published.");

    log.info("JavaFX start() phase complete.");
  }

  /**
   * JavaFX lifecycle: stop phase. Runs on the JavaFX Application Thread during shutdown.
   *
   * <p>Publishes the shutdown event and releases all resources in reverse initialization order.
   */
  @Override
  public void stop() throws Exception {
    log.info("JavaFX stop() phase starting...");

    if (appContext != null) {
      // Publish shutdown event so subscribers can clean up
      appContext.eventBus().publish(new ApplicationShutdownEvent());
      log.info("ApplicationShutdownEvent published.");

      // Shutdown the operational runtime safely
      appContext.runtimeManager().stop();
      log.info("RuntimeManager stopped.");

      appContext.workspaceService().shutdown();
      log.info("Workspace service stopped.");

      appContext.viewManager().clearCache();
      appContext.eventBus().clearAll();
      appContext.databaseManager().shutdown();

      log.info("All resources released.");
    }

    log.info("JavaFX stop() phase complete.");
    log.info("========================================");
    log.info("FileX Application Shutdown Complete");
    log.info("========================================");
  }

  /**
   * Controller factory for the main layout. Delegates to the same factory logic used by
   * ViewManager.
   */
  private Object createController(Class<?> controllerClass, AppContext ctx) {
    try {
      return controllerClass.getDeclaredConstructor(AppContext.class).newInstance(ctx);
    } catch (Exception e) {
      throw new RuntimeException(
          "Failed to instantiate controller: " + controllerClass.getName(), e);
    }
  }

  /** Application entry point. Launches the JavaFX application. */
  public static void main(String[] args) {
    log.info("FileX main() invoked. Launching JavaFX application...");
    launch(args);
  }
}
