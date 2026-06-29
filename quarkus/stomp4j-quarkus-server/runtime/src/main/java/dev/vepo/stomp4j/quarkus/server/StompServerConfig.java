package dev.vepo.stomp4j.quarkus.server;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import dev.vepo.stomp4j.commons.TransportType;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "stomp4j.server")
public interface StompServerConfig {

    interface Channel {

        @WithDefault("5500")
        int port();

        @WithDefault("TCP")
        TransportType type();
    }

    List<Channel> channels();

    @WithDefault("true")
    boolean enabled();

    @WithDefault("30S")
    Duration heartbeat();

    @WithDefault("stomp4j")
    String serverName();
}
