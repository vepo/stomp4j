package dev.vepo.stomp4j.spring.boot.autoconfigure.client;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ReflectionUtils;

import dev.vepo.stomp4j.client.AckMode;

public class StompListenerEndpointRegistrar implements ApplicationContextAware, SmartLifecycle {

    record RegisteredEndpoint(Object bean, Method method, String destination, AckMode ackMode) {}

    private final StompClientConnectionManager connectionManager;
    private final StompListenerMethodInvoker invoker;
    private ApplicationContext applicationContext;
    private final List<RegisteredEndpoint> endpoints = new ArrayList<>();

    private boolean running;

    public StompListenerEndpointRegistrar(StompClientConnectionManager connectionManager,
                                          StompListenerMethodInvoker invoker) {
        this.connectionManager = connectionManager;
        this.invoker = invoker;
    }

    private void discoverEndpoints() {
        for (var beanName : applicationContext.getBeanDefinitionNames()) {
            var bean = applicationContext.getBean(beanName);
            var targetClass = AopUtils.getTargetClass(bean);
            ReflectionUtils.doWithMethods(targetClass, method -> registerMethod(bean, method),
                                          method -> AnnotatedElementUtils.hasAnnotation(method, StompListener.class));
        }
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 99;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void registerMethod(Object bean, Method method) {
        var annotation = AnnotatedElementUtils.findMergedAnnotation(method, StompListener.class);
        if (annotation == null) {
            return;
        }
        endpoints.add(new RegisteredEndpoint(bean, method, annotation.destination(), annotation.ackMode()));
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void start() {
        if (running) {
            return;
        }
        discoverEndpoints();
        var client = connectionManager.client();
        for (var endpoint : endpoints) {
            client.subscribe(endpoint.destination(), endpoint.ackMode(), delivery -> invoker.invoke(endpoint, delivery));
        }
        running = true;
    }

    @Override
    public void stop() {
        running = false;
        endpoints.clear();
    }
}
