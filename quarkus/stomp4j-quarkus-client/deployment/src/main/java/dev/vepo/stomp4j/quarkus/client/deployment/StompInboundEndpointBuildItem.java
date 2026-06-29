package dev.vepo.stomp4j.quarkus.client.deployment;

import dev.vepo.stomp4j.quarkus.client.StompInboundEndpoint;
import io.quarkus.builder.item.SimpleBuildItem;

public final class StompInboundEndpointBuildItem extends SimpleBuildItem {

    private final StompInboundEndpoint endpoint;

    public StompInboundEndpointBuildItem(StompInboundEndpoint endpoint) {
        this.endpoint = endpoint;
    }

    public StompInboundEndpoint endpoint() {
        return endpoint;
    }
}
