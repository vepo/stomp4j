package dev.vepo.stomp4j.quarkus.client.deployment;

import java.util.ArrayList;
import java.util.List;

import dev.vepo.stomp4j.client.AckMode;
import dev.vepo.stomp4j.quarkus.client.Acknowledgment;
import dev.vepo.stomp4j.quarkus.client.StompDestination;
import dev.vepo.stomp4j.quarkus.client.StompDispatchMode;
import dev.vepo.stomp4j.quarkus.client.StompClientConfig;
import dev.vepo.stomp4j.quarkus.client.StompClientFactory;
import dev.vepo.stomp4j.quarkus.client.StompInboundEndpoint;
import dev.vepo.stomp4j.quarkus.client.StompInboundEndpointRegistry;
import dev.vepo.stomp4j.quarkus.client.StompInboundEventProducer;
import dev.vepo.stomp4j.quarkus.client.StompInboundMessage;
import dev.vepo.stomp4j.quarkus.client.StompInboundRegistrar;
import dev.vepo.stomp4j.quarkus.client.StompOutboundAsyncObserver;
import dev.vepo.stomp4j.quarkus.client.StompOutboundEventProducer;
import dev.vepo.stomp4j.quarkus.client.StompOutboundSyncObserver;
import dev.vepo.stomp4j.quarkus.client.StompSession;
import dev.vepo.stomp4j.quarkus.cdi.StompAsync;
import dev.vepo.stomp4j.quarkus.cdi.StompSync;
import io.quarkus.arc.processor.DotNames;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.BeanArchiveIndexBuildItem;
import io.quarkus.deployment.builditem.ConfigMappingBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.runtime.StartupTask;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;

import static io.quarkus.deployment.annotations.ExecutionTime.RUNTIME_INIT;

public class StompClientProcessor {

    private static final DotName STOMP_INBOUND_MESSAGE = DotName.createSimple(StompInboundMessage.class.getName());
    private static final DotName STOMP_DESTINATION = DotName.createSimple(StompDestination.class.getName());
    private static final DotName STOMP_SYNC = DotName.createSimple(StompSync.class.getName());
    private static final DotName STOMP_ASYNC = DotName.createSimple(StompAsync.class.getName());
    private static final DotName ACKNOWLEDGMENT = DotName.createSimple(Acknowledgment.class.getName());
    private static final DotName OBSERVES_ASYNC = DotName.createSimple("jakarta.enterprise.event.ObservesAsync");

    private AckMode ackMode(MethodInfo method) {
        for (Type parameter : method.parameterTypes()) {
            if (ACKNOWLEDGMENT.equals(parameter.name())) {
                return AckMode.CLIENT_INDIVIDUAL;
            }
        }
        return AckMode.AUTO;
    }

    @BuildStep
    void collectInboundObservers(BeanArchiveIndexBuildItem beanArchiveIndex,
                                 BuildProducer<StompInboundEndpointBuildItem> endpoints) {
        var index = beanArchiveIndex.getIndex();
        for (ClassInfo beanClass : index.getKnownClasses()) {
            for (MethodInfo method : beanClass.methods()) {
                var endpoint = endpointForMethod(method);
                if (endpoint != null) {
                    endpoints.produce(new StompInboundEndpointBuildItem(endpoint));
                }
            }
        }
    }

    private String destination(MethodInfo method, int parameterIndex) {
        for (AnnotationInstance annotation : method.annotations()) {
            if (!annotation.target().kind().equals(org.jboss.jandex.AnnotationTarget.Kind.METHOD_PARAMETER)) {
                continue;
            }
            if (annotation.target().asMethodParameter().position() != parameterIndex) {
                continue;
            }
            if (annotation.name().equals(STOMP_DESTINATION)) {
                return annotation.value().asString();
            }
        }
        return null;
    }

    private StompDispatchMode dispatchMode(MethodInfo method, int parameterIndex) {
        for (AnnotationInstance annotation : method.annotations()) {
            if (!annotation.target().kind().equals(org.jboss.jandex.AnnotationTarget.Kind.METHOD_PARAMETER)) {
                continue;
            }
            if (annotation.target().asMethodParameter().position() != parameterIndex) {
                continue;
            }
            if (annotation.name().equals(STOMP_SYNC)) {
                return StompDispatchMode.SYNC;
            }
            if (annotation.name().equals(STOMP_ASYNC) || annotation.name().equals(OBSERVES_ASYNC)) {
                return StompDispatchMode.ASYNC;
            }
        }
        return StompDispatchMode.ASYNC;
    }

    private StompInboundEndpoint endpointForMethod(MethodInfo method) {
        for (var index = 0; index < method.parametersCount(); index++) {
            if (!STOMP_INBOUND_MESSAGE.equals(method.parameterType(index).name())) {
                continue;
            }
            if (!observesParameter(method, index)) {
                continue;
            }
            var destination = destination(method, index);
            if (destination == null) {
                continue;
            }
            return new StompInboundEndpoint(destination, dispatchMode(method, index), ackMode(method));
        }
        return null;
    }

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem("stomp4j-client");
    }

    private boolean observesParameter(MethodInfo method, int parameterIndex) {
        for (AnnotationInstance annotation : method.annotations()) {
            if (!annotation.target().kind().equals(org.jboss.jandex.AnnotationTarget.Kind.METHOD_PARAMETER)) {
                continue;
            }
            if (annotation.target().asMethodParameter().position() != parameterIndex) {
                continue;
            }
            if (annotation.name().equals(DotNames.OBSERVES) || annotation.name().equals(OBSERVES_ASYNC)) {
                return true;
            }
        }
        return false;
    }

    @BuildStep
    void registerBeans(BuildProducer<AdditionalBeanBuildItem> beans) {
        beans.produce(AdditionalBeanBuildItem.builder()
                                             .addBeanClasses(
                                                             StompSession.class,
                                                             StompClientFactory.class,
                                                             StompInboundEndpointRegistry.class,
                                                             StompInboundRegistrar.class,
                                                             StompOutboundEventProducer.class,
                                                             StompInboundEventProducer.class,
                                                             StompOutboundSyncObserver.class,
                                                             StompOutboundAsyncObserver.class)
                                             .setUnremovable()
                                             .build());
    }

    @BuildStep
    void registerConfigMapping(BuildProducer<ConfigMappingBuildItem> configMappings) {
        configMappings.produce(new ConfigMappingBuildItem(StompClientConfig.class, "stomp4j.client"));
    }

    @BuildStep
    @Record(RUNTIME_INIT)
    StartupTask registerInboundEndpoints(StompInboundRecorder recorder,
                                         List<StompInboundEndpointBuildItem> items) {
        var endpoints = new ArrayList<StompInboundEndpoint>();
        for (var item : items) {
            endpoints.add(item.endpoint());
        }
        return recorder.register(endpoints);
    }
}
