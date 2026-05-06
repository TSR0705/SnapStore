package com.filex.ui;

import com.filex.app.AppContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;

/**
 * Factory for instantiating JavaFX controllers with dependency injection.
 *
 * <p>Controllers must declare a single-argument constructor accepting
 * {@link AppContext}. The factory uses reflection to invoke this constructor
 * and inject the application's root dependency container.
 *
 * <p>This approach keeps controllers decoupled from static state and
 * makes them trivially testable — tests can construct controllers with
 * a mock {@link AppContext}.
 */
final class ControllerFactory {

    private static final Logger log = LoggerFactory.getLogger(ControllerFactory.class);

    private ControllerFactory() {
        // Non-instantiable utility
    }

    /**
     * Creates a controller instance of the given class, injecting the
     * {@link AppContext} via constructor.
     *
     * @param controllerClass the controller class to instantiate
     * @param appContext      the application context to inject
     * @return a new controller instance
     * @throws ViewLoadException if the controller cannot be instantiated
     */
    static Object create(Class<?> controllerClass, AppContext appContext) {
        try {
            Constructor<?> constructor = controllerClass.getDeclaredConstructor(AppContext.class);
            constructor.setAccessible(true);
            Object instance = constructor.newInstance(appContext);
            log.debug("Instantiated controller: {}", controllerClass.getSimpleName());
            return instance;
        } catch (NoSuchMethodException e) {
            throw new ViewLoadException(
                    "Controller " + controllerClass.getName() +
                    " must declare a constructor accepting AppContext.", e
            );
        } catch (Exception e) {
            throw new ViewLoadException(
                    "Failed to instantiate controller: " + controllerClass.getName(), e
            );
        }
    }
}
