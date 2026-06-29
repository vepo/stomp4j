package dev.vepo.stomp4j.samples.plain.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import dev.vepo.stomp4j.client.AckMode;
import dev.vepo.stomp4j.client.SendOptions;
import dev.vepo.stomp4j.client.StompClient;
import dev.vepo.stomp4j.client.UserCredential;

/**
 * Plain-Java counterpart of {@code spring-client-sample}:
 * <ul>
 *   <li>Consumes {@code /queue/demo.in} with manual ACK</li>
 *   <li>Exposes {@code POST /publish} to send to {@code /queue/demo.out} with broker receipt</li>
 * </ul>
 */
public final class PlainClientSample {

    private static final Logger logger = LoggerFactory.getLogger(PlainClientSample.class);
    private static final String INBOUND_QUEUE = "/queue/demo.in";
    private static final String OUTBOUND_QUEUE = "/queue/demo.out";

    private PlainClientSample() {}

    public static void main(String[] args) throws Exception {
        var brokerUrl = env("STOMP4J_CLIENT_URL", "stomp://localhost:61613");
        var username = env("STOMP4J_CLIENT_USERNAME", "user");
        var password = env("STOMP4J_CLIENT_PASSWORD", "passwd");
        var httpPort = Integer.parseInt(env("HTTP_PORT", "8082"));

        try (var client = StompClient.create(brokerUrl, new UserCredential(username, password))) {
            client.connect();
            client.subscribe(INBOUND_QUEUE, AckMode.CLIENT_INDIVIDUAL, delivery -> {
                logger.info("Inbound message on {}: {}", INBOUND_QUEUE, delivery.body());
                delivery.ack();
            });

            var httpServer = startPublishEndpoint(client, httpPort);
            logger.info("Plain client sample ready — POST http://localhost:{}/publish", httpPort);
            client.join();
            httpServer.stop(0);
        }
    }

    private static HttpServer startPublishEndpoint(StompClient client, int httpPort) throws IOException {
        var httpServer = HttpServer.create(new InetSocketAddress(httpPort), 0);
        httpServer.setExecutor(Executors.newCachedThreadPool());
        httpServer.createContext("/publish", exchange -> handlePublish(client, exchange));
        httpServer.start();
        return httpServer;
    }

    private static void handlePublish(StompClient client, HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }
        try {
            var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            var receipt = client.send(OUTBOUND_QUEUE,
                                      body,
                                      SendOptions.builder()
                                                 .receipt(true)
                                                 .receiptTimeout(Duration.ofSeconds(30))
                                                 .build());
            receipt.completion().get();
            logger.info("Published to {} with receipt {}", OUTBOUND_QUEUE, receipt.receiptId());
            var response = "sent".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            exchange.sendResponseHeaders(500, -1);
        } catch (Exception ex) {
            logger.error("Publish failed", ex);
            exchange.sendResponseHeaders(500, -1);
        } finally {
            exchange.close();
        }
    }

    private static String env(String name, String defaultValue) {
        return Objects.requireNonNullElse(System.getenv(name), defaultValue);
    }
}
