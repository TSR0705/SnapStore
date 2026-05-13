package com.filex.ui;

import com.filex.app.AppContext;
import com.filex.workspace.WorkspaceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;

/**
 * Factory for instantiating JavaFX controllers with dependency injection.
 *
 * <p>Controllers can declare constructors accepting:
 * <ul>
 *   <li>{@link AppContext} only</li>
 *   <li>{@link AppContext} and {@link WorkspaceService}</li>
 * </ul>
 *
 * <p>The factory uses reflection to find the appropriate constructor
 * and inject dependencies.
 *
 * <p>This approach keeps controllers decoupled from static state and
 * makes them trivially testable — tests can construct controllers with
 * mock dependencies.
 */
public final class ControllerFactory {

    private static final Logger log = LoggerFactory.getLogger(ControllerFactory.class);

    private ControllerFactory() {
        // Non-instantiable utility
    }

    /**
     * Creates a controller instance of the given class, injecting dependencies
     * via constructor.
     *
     * @param controllerClass the controller class to instantiate
     * @param appContext      the application context to inject
     * @return a new controller instance
     * @throws ViewLoadException if the controller cannot be instantiated
     */
    public static Object create(Class<?> controllerClass, AppContext appContext) {
        try {
            // Try AppContext + WorkspaceService constructor first (for investigation controllers)
            try {
                Constructor<?> constructor = controllerClass.getDeclaredConstructor(
                        AppContext.class, WorkspaceService.class);
                constructor.setAccessible(true);
                WorkspaceService workspaceService = appContext.workspaceService();
                Object instance = constructor.newInstance(appContext, workspaceService);
                log.debug("Instantiated controller with WorkspaceService: {}", 
                        controllerClass.getSimpleName());
                return instance;
            } catch (NoSuchMethodException e) {
                // Fall through to AppContext-only constructor
            }

            // Try AppContext-only constructor
            Constructor<?> constructor = controllerClass.getDeclaredConstructor(AppContext.class);
            constructor.setAccessible(true);
            Object instance = constructor.newInstance(appContext);
            log.debug("Instantiated controller: {}", controllerClass.getSimpleName());
            return instance;

        } catch (NoSuchMethodException e) {
            throw new ViewLoadException(
                    "Controller " + controllerClass.getName() +
                    " must declare a constructor accepting AppContext or (AppContext, WorkspaceService).", e
            );
        } catch (Exception e) {
            throw new ViewLoadException(
                    "Failed to instantiate controller: " + controllerClass.getName(), e
            );
        }
    }
}
