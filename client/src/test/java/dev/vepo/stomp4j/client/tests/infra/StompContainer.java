package dev.vepo.stomp4j.client.tests.infra;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * <p>
 * <b>Responsibilities</b>
 * </p>
 * <ul>
 * <li><b>Knowing:</b> Whether the shared Artemis broker has been started for
 * this JVM.</li>
 * <li><b>Doing:</b> Start broker on demand; resolve
 * {@link StompActiveMqContainer} test parameters.</li>
 * </ul>
 * <p>
 * <b>Collaborators:</b> {@link StompActiveMqContainer}
 * </p>
 * <p>
 * <b>Not responsible for:</b> test assertions, destinations, or JMS publishing.
 * </p>
 */
public class StompContainer implements BeforeAllCallback, ParameterResolver {
    private static final Object LOCK = new Object();
    private static StompActiveMqContainer stomp;
    private static boolean shutdownHookRegistered;

    public static StompActiveMqContainer broker() {
        synchronized (LOCK) {
            if (stomp == null) {
                stomp = new StompActiveMqContainer();
                registerShutdownHook();
            }
            return stomp;
        }
    }

    public static void ensureStarted() {
        synchronized (LOCK) {
            if (stomp == null) {
                stomp = new StompActiveMqContainer();
                registerShutdownHook();
            }
            if (!stomp.isRunning()) {
                stomp.start();
            }
        }
    }

    private static void registerShutdownHook() {
        if (!shutdownHookRegistered) {
            shutdownHookRegistered = true;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                synchronized (LOCK) {
                    if (stomp != null && stomp.isRunning()) {
                        stomp.stop();
                    }
                }
            }, "stomp4j-client-broker-shutdown"));
        }
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        ensureStarted();
    }

    @Override
    public @Nullable Object resolveParameter(ParameterContext arg0, ExtensionContext arg1)
            throws ParameterResolutionException {
        return broker();
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        return parameterContext.getParameter().getType() == StompActiveMqContainer.class;
    }
}
