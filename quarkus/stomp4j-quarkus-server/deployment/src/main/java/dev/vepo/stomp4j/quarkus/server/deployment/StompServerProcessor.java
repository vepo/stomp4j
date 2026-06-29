package dev.vepo.stomp4j.quarkus.server.deployment;

import dev.vepo.stomp4j.quarkus.server.StompServerConfig;
import dev.vepo.stomp4j.quarkus.server.StompServerEventProducer;
import dev.vepo.stomp4j.quarkus.server.StompServerOutboundObserver;
import dev.vepo.stomp4j.quarkus.server.StompServerProducer;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.ConfigMappingBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;

public class StompServerProcessor {

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem("stomp4j-server");
    }

    @BuildStep
    void registerBeans(BuildProducer<AdditionalBeanBuildItem> beans) {
        beans.produce(AdditionalBeanBuildItem.builder()
                                             .addBeanClasses(
                                                             StompServerProducer.class,
                                                             StompServerEventProducer.class,
                                                             StompServerOutboundObserver.class)
                                             .setUnremovable()
                                             .build());
    }

    @BuildStep
    void registerConfigMapping(BuildProducer<ConfigMappingBuildItem> configMappings) {
        configMappings.produce(new ConfigMappingBuildItem(StompServerConfig.class, "stomp4j.server"));
    }
}
