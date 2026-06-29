package dev.vepo.stomp4j.server;

import java.util.Optional;

public interface StompSession {

    Optional<String> login();

    String version();

    OutboundChannel outboundChannel();
}
