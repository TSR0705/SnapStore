package com.filex.controller;

import com.filex.app.AppContext;
import com.filex.investigation.ReplayNavigationService;
import com.filex.investigation.TimelineEventSummary;
import com.filex.investigation.replay.ReplayCursor;
import com.filex.ui.ControllerFactory;
import com.filex.workspace.WorkspaceService;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith({ApplicationExtension.class, MockitoExtension.class})
public class ReplayControllerUITest {

    static {
        System.setProperty("testfx.robot", "glass");
        System.setProperty("testfx.headless", "true");
        System.setProperty("prism.order", "sw");
        System.setProperty("prism.text", "t2k");
        System.setProperty("java.awt.headless", "true");
    }

    @Mock
    private AppContext appContext;

    @Mock
    private WorkspaceService workspaceService;

    private ReplayController controller;

    @Start
    public void start(Stage stage) throws Exception {
        // We mock workspace state so initialize() won't crash
        com.filex.workspace.WorkspaceState state = com.filex.workspace.WorkspaceState.builder()
                .activeIncidentId("INC-TEST-123")
                .build();
        when(workspaceService.currentState()).thenReturn(state);

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/filex/view/replay.fxml"));
        loader.setControllerFactory(param -> new ReplayController(appContext, workspaceService));
        Parent root = loader.load();
        controller = loader.getController();

        stage.setScene(new Scene(root, 800, 600));
        stage.show();
    }

    @BeforeEach
    public void setup() {
        // Wait for FX thread to process any initial load
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    public void testFxWiringAndInitialState(FxRobot robot) {
        // Assert wiring
        assertNotNull(controller);
        Button jumpToStartBtn = robot.lookup("#jumpToStartButton").queryButton();
        assertNotNull(jumpToStartBtn);

        // Verify async load was called during initialize()
        verify(workspaceService).replayFromCheckpointAsync(any(ReplayCursor.class), any(), any());
    }

    @Test
    public void testReplayTimelineDisplay(FxRobot robot) {
        // Simulate a successful async response
        TimelineEventSummary event1 = TimelineEventSummary.builder()
                .timelineId("1")
                .incidentId("INC-TEST-123")
                .eventType("PROCESS_CREATE")
                .description("Test process")
                .timestamp(Instant.now())
                .sequenceNumber(1)
                .build();

        ReplayNavigationService.ReplayWindow window = new ReplayNavigationService.ReplayWindow(
                List.of(event1), Instant.now(), Instant.now(), 0, 500, 1, false);

        Platform.runLater(() -> {
            // ReplayController.displayReplay is private, but it gets called via the callback from replayFromCheckpointAsync
            // In the test, we'll capture the callback
        });
        
        // Instead of capturing, let's just make sure the mock captures the success callback
        doAnswer(invocation -> {
            Consumer<ReplayNavigationService.ReplayWindow> onSuccess = invocation.getArgument(1);
            onSuccess.accept(window);
            return null;
        }).when(workspaceService).replayFromCheckpointAsync(any(), any(), any());

        Platform.runLater(() -> {
            controller.loadReplay("INC-TEST-123");
        });
        WaitForAsyncUtils.waitForFxEvents();

        ListView<TimelineEventSummary> listView = robot.lookup("#replayListView").queryListView();
        assertEquals(1, listView.getItems().size());
        assertEquals("PROCESS_CREATE", listView.getItems().get(0).getEventType());
    }
}
