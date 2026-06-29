package dev.vepo.stomp4j.quarkus.client;

import java.time.Duration;
import java.util.Optional;

import dev.vepo.stomp4j.client.AckMode;
import dev.vepo.stomp4j.commons.TransportType;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "stomp4j.client")
public interface StompClientConfig {

    @WithDefault("CLIENT_INDIVIDUAL")
    AckMode defaultAckMode();

    @WithDefault("ASYNC")
    StompDispatchMode defaultDispatch();

    @WithDefault("true")
    boolean enabled();

    Optional<String> password();

    @WithDefault("30S")
    Duration receiptTimeout();

    Optional<TransportType> transportType();

    @WithDefault("stomp://localhost:61613")
    String url();

    Optional<String> username();
}
