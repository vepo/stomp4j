package dev.vepo.stomp4j.server;

import dev.vepo.stomp4j.commons.protocol.Headers;

public record StompMessage(
        String destination,
        String body,
        Headers headers,
        OutboundChannel sessionChannel
) {}
