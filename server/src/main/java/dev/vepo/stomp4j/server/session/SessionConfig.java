package dev.vepo.stomp4j.server.session;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import dev.vepo.stomp4j.server.auth.StompAuthenticator;

public record SessionConfig(
                            Optional<StompAuthenticator> authenticator,
                            List<String> supportedVersions,
                            Duration heartbeatInterval,
                            String serverName) {
    public static final List<String> DEFAULT_VERSIONS = List.of("1.2", "1.1", "1.0");
    public static final Duration DEFAULT_HEARTBEAT = Duration.ofSeconds(30);
    public static final String DEFAULT_SERVER_NAME = "stomp4j";

    public static SessionConfig defaults() {
        return new SessionConfig(Optional.empty(), DEFAULT_VERSIONS, DEFAULT_HEARTBEAT, DEFAULT_SERVER_NAME);
    }
}
