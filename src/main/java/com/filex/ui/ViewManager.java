package com.filex.ui;

import com.filex.app.AppContext;
import java.io.IOException;
import java.net.URL;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages JavaFX view lifecycle: loading, caching, and navigation.
 *
 * <p>Design principles:
 *
 * <ul>
 *   <li><b>Lazy loading</b> — views are loaded on first navigation, not at startup.
 *   <li><b>Caching</b> — once loaded, a view's {@link Node} is cached and reused. FXML is never
 *       parsed more than once per view.
 *   <li><b>Controller injection</b> — controllers receive the {@link AppContext} via a custom
 *       {@link FXMLLoader} controller factory, keeping them decoupled from static state.
 *   <li><b>Content area ownership</b> — the manager holds a reference to the main content {@link
 *       StackPane} and swaps children on navigation.
 * </ul>
 *
 * <p>Navigation is always performed on the JavaFX Application Thread. Callers are responsible for
 * thread safety.
 */
public final class ViewManager {

  private static final Logger log = LoggerFactory.getLogger(ViewManager.class);

  private final AppContext appContext;
  private final Map<ViewId, CachedView> viewCache = new EnumMap<>(ViewId.class);

  /** The main content area where views are swapped in/out. Set after stage init. */
  private StackPane contentArea;

  /** Currently active view identifier. */
  private ViewId currentView;

  /**
   * @param appContext root dependency container; passed to controller factory
   */
  public ViewManager(AppContext appContext) {
    this.appContext = Objects.requireNonNull(appContext, "appContext must not be null");
  }

  /**
   * Binds the content area pane that this manager will swap views into. Must be called once after
   * the main layout is loaded.
   *
   * @param contentArea the {@link StackPane} acting as the view host
   */
  public void bindContentArea(StackPane contentArea) {
    this.contentArea = Objects.requireNonNull(contentArea, "contentArea must not be null");
    log.debug("ViewManager content area bound.");
  }

  /**
   * Navigates to the specified view.
   *
   * <p>If the view has been loaded before, the cached node is used. Otherwise the FXML is loaded,
   * the controller is initialized, and the result is cached for future navigations.
   *
   * @param viewId the target view
   * @throws ViewLoadException if the FXML cannot be loaded
   */
  public void navigateTo(ViewId viewId) {
    Objects.requireNonNull(viewId, "viewId must not be null");

    if (contentArea == null) {
      throw new IllegalStateException("Content area not bound. Call bindContentArea() first.");
    }

    if (viewId == currentView) {
      log.debug("Already on view: {}. Navigation skipped.", viewId);
      return;
    }

    log.info("Navigating to view: {}", viewId);

    CachedView cached = viewCache.computeIfAbsent(viewId, this::loadView);

    contentArea.getChildren().setAll(cached.node());
    currentView = viewId;

    log.debug("Navigation complete. Active view: {}", currentView);
  }

  /**
   * Returns the currently active view identifier, or {@code null} if no navigation has occurred
   * yet.
   */
  public ViewId currentView() {
    return currentView;
  }

  /**
   * Pre-warms a view by loading it into the cache without displaying it. Useful for views that
   * should be instantly available on first navigation.
   *
   * @param viewId the view to pre-load
   */
  public void preload(ViewId viewId) {
    Objects.requireNonNull(viewId, "viewId must not be null");
    viewCache.computeIfAbsent(viewId, this::loadView);
    log.debug("Pre-loaded view: {}", viewId);
  }

  /**
   * Evicts a view from the cache, forcing a fresh load on next navigation. Use sparingly — only
   * when a view's state must be fully reset.
   *
   * @param viewId the view to evict
   */
  public void evict(ViewId viewId) {
    if (viewCache.remove(viewId) != null) {
      log.debug("Evicted view from cache: {}", viewId);
    }
  }

  /** Clears all cached views. Called during shutdown to release references. */
  public void clearCache() {
    viewCache.clear();
    contentArea = null;
    currentView = null;
    log.debug("ViewManager cache cleared.");
  }

  // -------------------------------------------------------------------------
  // Private helpers
  // -------------------------------------------------------------------------

  private CachedView loadView(ViewId viewId) {
    log.debug("Loading FXML for view: {}", viewId);

    URL fxmlUrl = getClass().getResource(viewId.fxmlPath());
    if (fxmlUrl == null) {
      throw new ViewLoadException("FXML resource not found: " + viewId.fxmlPath());
    }

    FXMLLoader loader = new FXMLLoader(fxmlUrl);

    // Controller factory: inject AppContext into every controller
    loader.setControllerFactory(
        controllerClass -> ControllerFactory.create(controllerClass, appContext));

    try {
      Node node = loader.load();
      Object controller = loader.getController();
      log.info(
          "View loaded: {} → controller: {}",
          viewId,
          controller != null ? controller.getClass().getSimpleName() : "none");
      return new CachedView(node, controller);
    } catch (IOException e) {
      throw new ViewLoadException("Failed to load FXML: " + viewId.fxmlPath(), e);
    }
  }

  // -------------------------------------------------------------------------
  // Inner types
  // -------------------------------------------------------------------------

  /** Holds a loaded view node and its associated controller. */
  private record CachedView(Node node, Object controller) {}
}
