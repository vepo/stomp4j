package dev.vepo.stomp4j.client.internal;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.vepo.stomp4j.client.protocol.Stomp;
import dev.vepo.stomp4j.client.transport.Transport;
import dev.vepo.stomp4j.commons.protocol.Header;
import dev.vepo.stomp4j.commons.protocol.Message;

/**
 * <p>
 * <b>Responsibilities</b>
 * </p>
 * <ul>
 * <li><b>Doing:</b> Negotiate heart-beat intervals from {@code CONNECTED} and
 * schedule outbound heart-beat frames; run deferred outbound work on the same
 * executor so transport read threads never block on {@code send}.</li>
 * </ul>
 * <p>
 * <b>Collaborators:</b> {@link Transport}, {@link Stomp}
 * </p>
 * <p>
 * <b>Not responsible for:</b> CONNECT negotiation, subscription routing.
 * </p>
 */
final class ClientHeartbeat {

    private static final Logger logger = LoggerFactory.getLogger(ClientHeartbeat.class);
    static final Duration DEFAULT_HEART_BEAT_INTERVAL = Duration.ofSeconds(30);

    private final ScheduledExecutorService executor;
    private ScheduledFuture<?> heartBeatTask;

    ClientHeartbeat(ScheduledExecutorService executor) {
        this.executor = executor;
    }

    void negotiateAndStart(Message connected, Transport transport, Supplier<Stomp> protocol) {
        stop();
        if (!protocol.get().hasHeartBeat()) {
            return;
        }
        logger.info("Heart beat is enabled");
        logger.atInfo().addArgument(() -> connected.headers().get(Header.HEART_BEAT)).log("Heart beat interval: {}");
        connected.headers()
                 .get(Header.HEART_BEAT)
                 .map(heartBeatInterval -> {
                     String[] heartBeats = heartBeatInterval.split(",");
                     var serverExpectMs = Integer.parseInt(heartBeats[1].trim());
                     var clientSendMs = DEFAULT_HEART_BEAT_INTERVAL.toMillis();
                     return Math.round(0.7 * Math.max(clientSendMs, serverExpectMs));
                 })
                 .ifPresent(interval -> {
                     if (interval > 0) {
                         logger.info("Setting up heart beat with interval: {}", interval);
                         var checkPeriodMs = Math.max(1_000L, interval / 4);
                         heartBeatTask = executor.scheduleAtFixedRate(() -> {
                             try {
                                 if (transport.outboundSilentTime() >= interval) {
                                     transport.send(protocol.get().heartBeatMessage());
                                 }
                             } catch (RuntimeException ex) {
                                 logger.debug("Heartbeat send failed: {}", ex.getMessage());
                             }
                         }, checkPeriodMs, checkPeriodMs, TimeUnit.MILLISECONDS);
                     } else {
                         logger.info("Heart beat interval is zero. Disabling heart beat");
                     }
                 });
    }

    void scheduleOutbound(Runnable task) {
        executor.execute(task);
    }

    void shutdown() {
        stop();
        executor.shutdown();
    }

    void stop() {
        if (Objects.nonNull(heartBeatTask)) {
            heartBeatTask.cancel(false);
            heartBeatTask = null;
        }
    }
}
