package dev.vepo.stomp4j.quarkus.client;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import dev.vepo.stomp4j.client.AckMode;
import dev.vepo.stomp4j.quarkus.cdi.StompAsync;
import dev.vepo.stomp4j.quarkus.cdi.StompSync;
import io.quarkus.arc.Arc;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.spi.Bean;

public final class StompInboundClasspathScanner {

    private static AckMode ackMode(Parameter[] parameters) {
        for (var parameter : parameters) {
            if (Acknowledgment.class.isAssignableFrom(parameter.getType())) {
                return AckMode.CLIENT_INDIVIDUAL;
            }
        }
        return AckMode.AUTO;
    }

    private static String destination(Parameter parameter) {
        var annotation = parameter.getAnnotation(StompDestination.class);
        return annotation == null ? null : annotation.value();
    }

    private static StompDispatchMode dispatchMode(Parameter parameter) {
        if (parameter.isAnnotationPresent(StompSync.class)) {
            return StompDispatchMode.SYNC;
        }
        if (parameter.isAnnotationPresent(StompAsync.class)
                || parameter.isAnnotationPresent(ObservesAsync.class)) {
            return StompDispatchMode.ASYNC;
        }
        return StompDispatchMode.ASYNC;
    }

    private static StompInboundEndpoint endpointForMethod(Method method) {
        var parameters = method.getParameters();
        for (var index = 0; index < parameters.length; index++) {
            var parameter = parameters[index];
            if (!StompInboundMessage.class.isAssignableFrom(parameter.getType())) {
                continue;
            }
            if (!observesParameter(parameter)) {
                continue;
            }
            var destination = destination(parameter);
            if (destination == null) {
                continue;
            }
            return new StompInboundEndpoint(destination, dispatchMode(parameter), ackMode(parameters));
        }
        return null;
    }

    private static boolean observesParameter(Parameter parameter) {
        return parameter.isAnnotationPresent(Observes.class)
                || parameter.isAnnotationPresent(ObservesAsync.class);
    }

    public static List<StompInboundEndpoint> scanApplicationBeans() {
        var endpoints = new ArrayList<StompInboundEndpoint>();
        Set<Bean<?>> beans = Arc.container().beanManager().getBeans(Object.class, Any.Literal.INSTANCE);
        for (Bean<?> bean : beans) {
            scanType(bean.getBeanClass(), endpoints);
        }
        return endpoints;
    }

    private static void scanType(Class<?> type, List<StompInboundEndpoint> endpoints) {
        if (type == null || type == Object.class) {
            return;
        }
        for (var method : type.getDeclaredMethods()) {
            var endpoint = endpointForMethod(method);
            if (endpoint != null && endpoints.stream().noneMatch(existing -> existing.destination().equals(endpoint.destination()))) {
                endpoints.add(endpoint);
            }
        }
        scanType(type.getSuperclass(), endpoints);
    }

    private StompInboundClasspathScanner() {}
}
