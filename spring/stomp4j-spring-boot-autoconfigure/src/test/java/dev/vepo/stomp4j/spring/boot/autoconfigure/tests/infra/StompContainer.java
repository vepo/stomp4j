package dev.vepo.stomp4j.spring.boot.autoconfigure.tests.infra;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

public class StompContainer implements BeforeAllCallback, AfterAllCallback, ParameterResolver {
    private static StompActiveMqContainer stomp;

    public static StompActiveMqContainer broker() {
        if (stomp == null) {
            stomp = new StompActiveMqContainer();
        }
        return stomp;
    }

    public static void ensureStarted() {
        if (!broker().isRunning()) {
            broker().start();
        }
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        if (stomp != null) {
            stomp.stop();
            stomp = null;
        }
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
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
