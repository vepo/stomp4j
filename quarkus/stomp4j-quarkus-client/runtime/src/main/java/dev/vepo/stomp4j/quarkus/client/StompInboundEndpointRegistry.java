package dev.vepo.stomp4j.quarkus.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class StompInboundEndpointRegistry {

    private final List<StompInboundEndpoint> endpoints = new ArrayList<>();

    public List<StompInboundEndpoint> endpoints() {
        return Collections.unmodifiableList(endpoints);
    }

    public void setEndpoints(List<StompInboundEndpoint> registered) {
        endpoints.clear();
        endpoints.addAll(registered);
    }
}
