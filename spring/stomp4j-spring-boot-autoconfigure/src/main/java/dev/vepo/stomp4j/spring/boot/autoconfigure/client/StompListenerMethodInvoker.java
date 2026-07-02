package dev.vepo.stomp4j.spring.boot.autoconfigure.client;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Objects;

import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.util.Arrays;

import dev.vepo.stomp4j.client.AckMode;
import dev.vepo.stomp4j.client.StompDelivery;
import dev.vepo.stomp4j.commons.protocol.Header;
import dev.vepo.stomp4j.commons.protocol.Headers;
import dev.vepo.stomp4j.integration.client.Acknowledgment;
import dev.vepo.stomp4j.integration.client.InboundAckPolicy;

public class StompListenerMethodInvoker {

    private final org.springframework.core.task.TaskExecutor taskExecutor;
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    public StompListenerMethodInvoker(org.springframework.core.task.TaskExecutor taskExecutor) {
        this.taskExecutor = taskExecutor;
    }

    private void applyAckRules(AckMode ackMode,
                               StompDelivery delivery,
                               Acknowledgment acknowledgment,
                               boolean threw) {
        InboundAckPolicy.afterInvocation(ackMode, delivery, acknowledgment, threw);
    }

    private java.util.Optional<String> headerValue(Headers headers, String name) {
        var byEnum = Arrays.stream(Header.values())
                           .filter(header -> header.value().equals(name))
                           .findFirst()
                           .flatMap(headers::get);
        return byEnum.isPresent() ? byEnum : headers.get(name);
    }

    public void invoke(StompListenerEndpointRegistrar.RegisteredEndpoint endpoint, StompDelivery delivery) {
        taskExecutor.execute(() -> invokeOnExecutor(endpoint, delivery));
    }

    private void invokeOnExecutor(StompListenerEndpointRegistrar.RegisteredEndpoint endpoint, StompDelivery delivery) {
        var acknowledgment = Acknowledgment.of(delivery);
        var args = resolveArguments(endpoint.method(), delivery, acknowledgment);
        var threw = false;
        try {
            endpoint.method().setAccessible(true);
            endpoint.method().invoke(endpoint.bean(), args);
        } catch (ReflectiveOperationException ex) {
            threw = true;
            throw new IllegalStateException("Failed to invoke @StompListener method %s".formatted(endpoint.method()), ex);
        } finally {
            applyAckRules(endpoint.ackMode(), delivery, acknowledgment, threw);
        }
    }

    private Object resolveArgument(Parameter parameter, StompDelivery delivery, Acknowledgment acknowledgment) {
        if (parameter.getType().equals(StompDelivery.class)) {
            return delivery;
        }
        if (parameter.getType().equals(Acknowledgment.class)) {
            return acknowledgment;
        }
        if (parameter.getType().equals(String.class)) {
            var header = AnnotatedElementUtils.findMergedAnnotation(parameter, StompHeader.class);
            if (header != null) {
                return headerValue(delivery.headers(), header.value()).orElse(null);
            }
            return delivery.body();
        }
        throw new IllegalStateException("Unsupported @StompListener parameter type: %s".formatted(parameter.getType()));
    }

    private Object[] resolveArguments(Method method, StompDelivery delivery, Acknowledgment acknowledgment) {
        var parameters = method.getParameters();
        var args = new Object[parameters.length];
        for (var index = 0; index < parameters.length; index++) {
            args[index] = resolveArgument(parameters[index], delivery, acknowledgment);
        }
        return args;
    }
}
