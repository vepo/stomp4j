package dev.vepo.stomp4j.quarkus.client.deployment;

import java.util.List;

import dev.vepo.stomp4j.quarkus.client.StompInboundEndpoint;
import dev.vepo.stomp4j.quarkus.client.StompInboundEndpointRegistry;
import io.quarkus.arc.Arc;
import io.quarkus.runtime.StartupContext;
import io.quarkus.runtime.StartupTask;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class StompInboundRecorder {

    public StartupTask register(List<StompInboundEndpoint> endpoints) {
        return new StartupTask() {
            @Override
            public void deploy(StartupContext startupContext) {
                Arc.container().instance(StompInboundEndpointRegistry.class)
                   .get()
                   .setEndpoints(endpoints);
            }
        };
    }
}
