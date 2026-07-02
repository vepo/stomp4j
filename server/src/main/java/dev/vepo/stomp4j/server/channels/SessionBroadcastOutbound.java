package dev.vepo.stomp4j.server.channels;

import java.util.Optional;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.slf4j.Logger;

import dev.vepo.stomp4j.commons.protocol.Message;
import dev.vepo.stomp4j.server.AcknowledgedOutboundChannel;
import dev.vepo.stomp4j.server.SubscriberAckListener;
import dev.vepo.stomp4j.server.session.Session;

/**
 * <p>
 * <b>Responsibilities</b>
 * </p>
 * <ul>
 * <li><b>Doing:</b> Broadcast an outbound STOMP frame to every active
 * {@link Session} on a transport channel.</li>
 * </ul>
 * <p>
 * <b>Not responsible for:</b> Encoding frames per session, subscription
 * matching — delegated to {@link Session#handle(Message, Optional)}.
 * </p>
 */
final class SessionBroadcastOutbound implements AcknowledgedOutboundChannel {

    private final Logger logger;
    private final IntSupplier sessionCount;
    private final Supplier<Stream<Session>> activeSessions;

    SessionBroadcastOutbound(Logger logger, IntSupplier sessionCount, Supplier<Stream<Session>> activeSessions) {
        this.logger = logger;
        this.sessionCount = sessionCount;
        this.activeSessions = activeSessions;
    }

    @Override
    public void send(Message message) {
        send(message, null);
    }

    @Override
    public void send(Message message, SubscriberAckListener listener) {
        logger.debug("Sending message to all active sessions: count={}", sessionCount.getAsInt());
        var ackListener = Optional.ofNullable(listener);
        activeSessions.get().forEach(session -> session.handle(message, ackListener));
    }
}
