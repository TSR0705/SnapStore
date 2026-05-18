package com.filex.runtime;

import com.filex.alert.AlertEngine;
import com.filex.alert.IncidentPersistenceSubscriber;
import com.filex.config.AppConfig;
import com.filex.detection.DetectionEngine;
import com.filex.engine.MonitoringEngine;
import com.filex.event.EventBus;
import com.filex.event.RuntimeState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

public class RuntimeManagerTest {

    @TempDir
    Path tempDir;

    private AppConfig config;
    private EventBus eventBus;
    private MonitoringEngine monitoringEngine;
    private DetectionEngine detectionEngine;
    private AlertEngine alertEngine;
    private IncidentPersistenceSubscriber persistenceSubscriber;
    private RuntimeManager runtimeManager;

    @BeforeEach
    void setUp() {
        config = mock(AppConfig.class);
        eventBus = mock(EventBus.class);
        monitoringEngine = mock(MonitoringEngine.class);
        detectionEngine = mock(DetectionEngine.class);
        alertEngine = mock(AlertEngine.class);
        persistenceSubscriber = mock(IncidentPersistenceSubscriber.class);

        runtimeManager = new RuntimeManager(
                config, eventBus, monitoringEngine, detectionEngine, alertEngine, persistenceSubscriber
        );
    }

    @Test
    void testStartupOrder() throws Exception {
        when(config.demoMode()).thenReturn(false);

        runtimeManager.start();

        InOrder inOrder = inOrder(persistenceSubscriber, detectionEngine, alertEngine);
        inOrder.verify(persistenceSubscriber).start();
        inOrder.verify(detectionEngine).start();
        inOrder.verify(alertEngine).start();

        assertEquals(RuntimeState.RUNNING, runtimeManager.getCurrentState());
    }

    @Test
    void testDemoModeActivation() throws Exception {
        Path demoPath = tempDir.resolve("demo-watch");
        when(config.demoMode()).thenReturn(true);
        when(config.demoMonitorPath()).thenReturn(demoPath);
        when(config.demoAutoCreatePath()).thenReturn(true);

        runtimeManager.start();

        verify(monitoringEngine).start(Collections.singletonList(demoPath));
        assertEquals(RuntimeState.RUNNING, runtimeManager.getCurrentState());
    }

    @Test
    void testShutdownOrder() {
        runtimeManager.start();
        runtimeManager.stop();

        InOrder inOrder = inOrder(monitoringEngine, detectionEngine, alertEngine, persistenceSubscriber);
        inOrder.verify(monitoringEngine).stop();
        inOrder.verify(detectionEngine).stop();
        inOrder.verify(alertEngine).stop();
        inOrder.verify(persistenceSubscriber).stop();

        assertEquals(RuntimeState.STOPPED, runtimeManager.getCurrentState());
    }

    @Test
    void testStartupFailure() throws Exception {
        doThrow(new RuntimeException("Simulated failure")).when(detectionEngine).start();

        runtimeManager.start();

        assertEquals(RuntimeState.FAILED, runtimeManager.getCurrentState());
    }
}
