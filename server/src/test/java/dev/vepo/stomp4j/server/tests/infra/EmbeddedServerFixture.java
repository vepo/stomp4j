package dev.vepo.stomp4j.server.tests.infra;

import java.util.Objects;
import java.util.Optional;

import dev.vepo.stomp4j.commons.TransportType;
import dev.vepo.stomp4j.server.AcknowledgedOutboundChannel;
import dev.vepo.stomp4j.server.MessageHandler;
import dev.vepo.stomp4j.server.OutboundChannel;
import dev.vepo.stomp4j.server.StompConnectionListener;
import dev.vepo.stomp4j.server.StompServer;
import dev.vepo.stomp4j.server.SubscriptionHandler;
import dev.vepo.stomp4j.server.auth.StompAuthenticator;

/**
 * <p><b>Responsibilities</b></p>
 * <ul>
 *   <li><b>Knowing:</b> Ephemeral TCP and WebSocket ports bound by the embedded server.</li>
 *   <li><b>Doing:</b> Start and stop {@link StompServer} on allocated ports for tests.</li>
 * </ul>
 * <p><b>Collaborators:</b> {@link EphemeralPorts}, {@link StompServer}</p>
 * <p><b>Not responsible for:</b> protocol assertions or destination naming.</p>
 */
public final class EmbeddedServerFixture implements AutoCloseable {

    public enum ClientTransport {
        TCP,
        WEB_SOCKET
    }

    private final StompServer server;
    private final Optional<Integer> tcpPort;
    private final Optional<Integer> webSocketPort;

    private EmbeddedServerFixture(StompServer server, Optional<Integer> tcpPort, Optional<Integer> webSocketPort) {
        this.server = server;
        this.tcpPort = tcpPort;
        this.webSocketPort = webSocketPort;
    }

    public static Builder builder() {
        return new Builder();
    }

    public StompServer server() {
        return server;
    }

    public OutboundChannel outboundChannel() {
        return server.outboundChannel();
    }

    public AcknowledgedOutboundChannel acknowledgedOutboundChannel() {
        return server.acknowledgedOutboundChannel();
    }

    public int tcpPort() {
        return tcpPort.orElseThrow(() -> new IllegalStateException("TCP channel was not configured"));
    }

    public int webSocketPort() {
        return webSocketPort.orElseThrow(() -> new IllegalStateException("WebSocket channel was not configured"));
    }

    public String stompTcpUrl() {
        return "stomp://localhost:%d".formatted(tcpPort());
    }

    public String stompSecureTcpUrl() {
        return "stomps://localhost:%d".formatted(tcpPort());
    }

    public String webSocketUrl() {
        return "ws://localhost:%d".formatted(webSocketPort());
    }

    public String secureWebSocketUrl() {
        return "wss://localhost:%d".formatted(webSocketPort());
    }

    public String clientUrl(ClientTransport transport) {
        return switch (transport) {
            case TCP -> stompTcpUrl();
            case WEB_SOCKET -> webSocketUrl();
        };
    }

    @Override
    public void close() {
        server.close();
    }

    public static final class Builder {
        private final StompServer.Builder delegate = StompServer.builder();
        private Optional<Integer> tcpPort = Optional.empty();
        private Optional<Integer> webSocketPort = Optional.empty();

        public Builder withTcp() {
            var port = EphemeralPorts.allocate();
            tcpPort = Optional.of(port);
            delegate.channel(TransportType.TCP, port);
            return this;
        }

        public Builder withWebSocket() {
            var port = EphemeralPorts.allocate();
            webSocketPort = Optional.of(port);
            delegate.channel(TransportType.WEB_SOCKET, port);
            return this;
        }

        public Builder authenticator(StompAuthenticator authenticator) {
            delegate.authenticator(authenticator);
            return this;
        }

        public Builder subscription(SubscriptionHandler subscriptionHandler) {
            delegate.subscription(subscriptionHandler);
            return this;
        }

        public Builder subscription(java.util.function.Predicate<String> accept) {
            delegate.subscription(accept::test);
            return this;
        }

        public Builder handler(MessageHandler messageHandler) {
            delegate.handler(messageHandler);
            return this;
        }

        public Builder connectionListener(StompConnectionListener connectionListener) {
            delegate.connectionListener(connectionListener);
            return this;
        }

        public Builder ssl(javax.net.ssl.SSLContext sslContext) {
            delegate.ssl(sslContext);
            return this;
        }

        public Builder ssl(javax.net.ssl.SSLContext sslContext, String keyStorePath, String keyStorePassword) {
            delegate.ssl(sslContext, keyStorePath, keyStorePassword);
            return this;
        }

        public EmbeddedServerFixture start() {
            Objects.requireNonNull(delegate, "server builder");
            if (tcpPort.isEmpty() && webSocketPort.isEmpty()) {
                withTcp();
            }
            return new EmbeddedServerFixture(delegate.start(), tcpPort, webSocketPort);
        }
    }
}
