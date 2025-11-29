package dev.vepo.stomp4j.tests.infra;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

public class StompContainer implements BeforeAllCallback, AfterAllCallback, ParameterResolver {
    private static StompActiveMqContainer stomp = new StompActiveMqContainer();

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        stomp.stop();
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        stomp.start();
    }

    @Override
    public @Nullable Object resolveParameter(ParameterContext arg0, ExtensionContext arg1)
            throws ParameterResolutionException {
        return stomp;
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        return parameterContext.getParameter().getType() == StompActiveMqContainer.class;
    }

}
