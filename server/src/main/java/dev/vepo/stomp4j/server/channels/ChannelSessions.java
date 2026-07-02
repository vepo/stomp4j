package dev.vepo.stomp4j.server.channels;

import dev.vepo.stomp4j.server.OutboundChannel;
import dev.vepo.stomp4j.server.session.Session;
import dev.vepo.stomp4j.server.session.SessionCloser;

/**
 * <p>
 * <b>Responsibilities</b>
 * </p>
 * <ul>
 * <li><b>Doing:</b> Construct a {@link Session} with the shared channel
 * listener, runtime configuration, and closer callback.</li>
 * </ul>
 * <p>
 * <b>Not responsible for:</b> Transport I/O, session registration, or
 * post-construction wiring.
 * </p>
 */
final class ChannelSessions {

    private final ChannelListener listener;
    private final ChannelRuntime runtime;
    private final SessionCloser sessionCloser;

    ChannelSessions(ChannelListener listener, ChannelRuntime runtime, SessionCloser sessionCloser) {
        this.listener = listener;
        this.runtime = runtime;
        this.sessionCloser = sessionCloser;
    }

    Session open(OutboundChannel outbound) {
        return new Session(outbound,
                           listener,
                           runtime.sessionConfig(),
                           sessionCloser,
                           runtime.heartbeatExecutor());
    }
}
