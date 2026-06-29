package dev.vepo.stomp4j.quarkus.client;

import dev.vepo.stomp4j.client.AckMode;

public record StompInboundEndpoint(
                                   String destination,
                                   StompDispatchMode dispatchMode,
                                   AckMode ackMode) {}
