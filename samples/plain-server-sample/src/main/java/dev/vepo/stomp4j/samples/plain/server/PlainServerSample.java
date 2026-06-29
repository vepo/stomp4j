package dev.vepo.stomp4j.samples.plain.server;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.vepo.stomp4j.commons.TransportType;
import dev.vepo.stomp4j.commons.protocol.Command;
import dev.vepo.stomp4j.commons.protocol.Header;
import dev.vepo.stomp4j.commons.protocol.Headers;
import dev.vepo.stomp4j.commons.protocol.Message;
import dev.vepo.stomp4j.server.StompServer;

/**
 * Plain-Java counterpart of {@code spring-server-sample}: embedded STOMP server
 * with chat echo, subscription policy, and CONNECT auth.
 */
public final class PlainServerSample {

    private static final Logger logger = LoggerFactory.getLogger(PlainServerSample.class);
    private static final String APP_CHAT_PREFIX = "/app/chat/";
    private static final String TOPIC_CHAT_PREFIX = "/topic/chat/";

    private static boolean acceptSubscription(String topic) {
        var allowed = topic.startsWith(TOPIC_CHAT_PREFIX) || topic.startsWith(APP_CHAT_PREFIX);
        if (!allowed) {
            logger.warn("Rejected subscription to {}", topic);
        }
        return allowed;
    }

    private static Message chatMessage(String appDestination, String body) {
        var room = appDestination.substring(APP_CHAT_PREFIX.length());
        return new Message(Command.SEND,
                           Headers.builder().with(Header.DESTINATION, TOPIC_CHAT_PREFIX + room).build(),
                           body);
    }

    private static void echoToTopic(String body, String appDestination) {
        var room = appDestination.substring(APP_CHAT_PREFIX.length());
        logger.info("Echo /app/chat/{} -> /topic/chat/{}: {}", room, room, body);
    }

    private static String env(String name, String defaultValue) {
        return Objects.requireNonNullElse(System.getenv(name), defaultValue);
    }

    public static void main(String[] args) throws InterruptedException {
        var tcpPort = Integer.parseInt(env("STOMP_TCP_PORT", "5510"));
        var webSocketPort = Integer.parseInt(env("STOMP_WS_PORT", "5511"));
        var shutdownLatch = new CountDownLatch(1);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down plain server sample");
            shutdownLatch.countDown();
        }));

        try (var server = StompServer.builder()
                                     .serverName("plain-server-sample")
                                     .heartbeat(Duration.ofSeconds(30))
                                     .channel(TransportType.TCP, tcpPort)
                                     .channel(TransportType.WEB_SOCKET, webSocketPort)
                                     .authenticator(credentials -> "demo".equals(credentials.username()))
                                     .subscription(PlainServerSample::acceptSubscription)
                                     .handler(message -> {
                                         var destination = message.destination();
                                         if (destination.startsWith(APP_CHAT_PREFIX)) {
                                             echoToTopic(message.body(), destination);
                                             message.sessionChannel()
                                                    .send(chatMessage(destination, message.body()));
                                         }
                                     })
                                     .start()) {

            logger.info("Plain server sample listening on stomp://localhost:{} and ws://localhost:{}/",
                        tcpPort,
                        webSocketPort);
            logger.info("Connect with login demo; subscribe /topic/chat/lobby; send to /app/chat/lobby");
            shutdownLatch.await();
        }
    }

    private PlainServerSample() {}
}
