package dev.vepo.stomp4j.spring.boot.autoconfigure.tests.infra;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

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
        if (!broker().isRunning()) {
            broker().start();
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
            }, "stomp4j-spring-broker-shutdown"));
        }
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        ensureStarted();
    }

    @Override
    public Object resolveParameter(ParameterContext arg0, ExtensionContext arg1)
            throws ParameterResolutionException {
        return broker();
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        return parameterContext.getParameter().getType() == StompActiveMqContainer.class;
    }
}
