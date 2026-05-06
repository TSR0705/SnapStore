package com.filex.controller;

import com.filex.app.AppContext;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller for the Overview landing view.
 *
 * <p>Phase 1A: This is a minimal placeholder view that confirms the
 * navigation system is working. It displays basic application info.
 *
 * <p>Phase 2+: This view will be replaced by a full Dashboard with
 * real-time telemetry, alert summaries, and system health indicators.
 */
public final class OverviewController {

    private static final Logger log = LoggerFactory.getLogger(OverviewController.class);

    private final AppContext appContext;

    @FXML private Label lblAppName;
    @FXML private Label lblAppVersion;
    @FXML private Label lblStatus;

    /**
     * Constructor-based dependency injection.
     */
    public OverviewController(AppContext appContext) {
        this.appContext = appContext;
    }

    /**
     * JavaFX lifecycle method — called after FXML injection completes.
     */
    @FXML
    public void initialize() {
        log.debug("OverviewController initialized.");

        lblAppName.setText(appContext.config().appName());
        lblAppVersion.setText("Version " + appContext.config().appVersion());
        lblStatus.setText("System Ready");

        log.info("Overview view rendered.");
    }
}
