package com.filex.controller;

import com.filex.app.AppContext;
import com.filex.ui.ViewId;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller for the main application shell.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Bind the content area to the {@link com.filex.ui.ViewManager}</li>
 *   <li>Handle sidebar navigation button clicks</li>
 *   <li>Delegate actual view loading to the ViewManager</li>
 * </ul>
 *
 * <p>This controller contains NO business logic. It is purely a
 * coordination layer between UI events and the ViewManager.
 */
public final class MainLayoutController {

    private static final Logger log = LoggerFactory.getLogger(MainLayoutController.class);

    private final AppContext appContext;

    @FXML private VBox sidebar;
    @FXML private StackPane contentArea;
    @FXML private Button btnOverview;
    @FXML private Button btnExit;

    /**
     * Constructor-based dependency injection.
     * Invoked by {@link com.filex.ui.ControllerFactory}.
     */
    public MainLayoutController(AppContext appContext) {
        this.appContext = appContext;
    }

    /**
     * JavaFX lifecycle method — called after FXML injection completes.
     */
    @FXML
    public void initialize() {
        log.debug("MainLayoutController initialized.");

        // Bind the content area to the ViewManager so it can swap views
        appContext.viewManager().bindContentArea(contentArea);

        // Navigate to the default landing view
        appContext.viewManager().navigateTo(ViewId.OVERVIEW);

        // Wire up navigation buttons
        btnOverview.setOnAction(e -> navigateToOverview());
        btnExit.setOnAction(e -> exitApplication());
    }

    // -------------------------------------------------------------------------
    // Navigation handlers
    // -------------------------------------------------------------------------

    private void navigateToOverview() {
        log.debug("User clicked Overview button.");
        appContext.viewManager().navigateTo(ViewId.OVERVIEW);
    }

    private void exitApplication() {
        log.info("User requested application exit.");
        Platform.exit();
    }
}
