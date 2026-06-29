package dev.vepo.stomp4j.server;

import dev.vepo.stomp4j.commons.TransportType;

public record TransportChannel(TransportType type, int port) {}