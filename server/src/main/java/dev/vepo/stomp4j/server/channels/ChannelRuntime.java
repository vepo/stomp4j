package dev.vepo.stomp4j.server.channels;

import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;

import dev.vepo.stomp4j.server.session.SessionConfig;
import dev.vepo.stomp4j.server.ssl.SslSettings;

public record ChannelRuntime(
        SessionConfig sessionConfig,
        Optional<SslSettings> sslSettings,
        ScheduledExecutorService heartbeatExecutor
) {}
