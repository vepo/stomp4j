package dev.vepo.stomp4j.server.channels;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.vepo.stomp4j.commons.protocol.Message;
import dev.vepo.stomp4j.server.OutboundChannel;
import dev.vepo.stomp4j.server.session.Session;
import dev.vepo.stomp4j.server.session.SessionCloser;
import dev.vepo.stomp4j.server.session.Status;

public class TcpChannel implements Channel {
    private record SessionAttachment(Session session, SocketChannel socket, SslEngineChannel sslChannel) {}

    private class TcpExternalOutboundChannel implements OutboundChannel {

        @Override
        public void send(Message message) {
            logger.debug("Sending message to all active sessions: count={}", sessionAttachments.size());
            sessionAttachments.values()
                              .stream()
                              .map(SessionAttachment::session)
                              .forEach(session -> session.handle(message));
        }
    }

    private class TcpSessionOutboundChannel implements OutboundChannel {

        private final SocketChannel socket;
        private final SslEngineChannel sslChannel;

        private TcpSessionOutboundChannel(SocketChannel socket, SslEngineChannel sslChannel) {
            this.socket = socket;
            this.sslChannel = sslChannel;
        }

        @Override
        public void send(Message message) {
            try {
                var encoded = message.encode().getBytes();
                if (Objects.nonNull(sslChannel)) {
                    sslChannel.write(encoded);
                } else {
                    socket.write(ByteBuffer.wrap(encoded));
                }
            } catch (IOException ex) {
                logger.error("Error sending message: %s".formatted(message), ex);
            }
        }
    }

    private static final Logger logger = LoggerFactory.getLogger(TcpChannel.class);

    private final int port;
    private final ChannelListener listener;
    private final ChannelRuntime runtime;
    private final ExecutorService threadPool;
    private final AtomicBoolean running;
    private final BufferPool bufferPool;
    private final Map<Session, SessionAttachment> sessionAttachments;
    private final TcpExternalOutboundChannel outboundChannel;
    private final SessionCloser sessionCloser;
    private Selector selector;
    private ServerSocketChannel channel;

    public TcpChannel(int port, ChannelListener listener, ChannelRuntime runtime) {
        this.port = port;
        this.listener = listener;
        this.runtime = runtime;
        this.threadPool = Executors.newFixedThreadPool(10);
        this.running = new AtomicBoolean(false);
        this.bufferPool = new BufferPool(10, 1024);
        this.sessionAttachments = new ConcurrentHashMap<>();
        this.outboundChannel = new TcpExternalOutboundChannel();
        this.sessionCloser = this::closeSession;
    }

    @Override
    public synchronized void start() {
        if (Objects.isNull(selector)) {
            logger.info("Starting TCP Channel at port {}", port);
            try {
                this.selector = Selector.open();
                this.channel = ServerSocketChannel.open();
                channel.configureBlocking(false);
                channel.bind(new InetSocketAddress(port));
                channel.register(selector, SelectionKey.OP_ACCEPT);

                this.running.set(true);
                this.threadPool.submit(this::accept);
                logger.info("TCP Channel started! port={}", port);
            } catch (IOException ex) {
                logger.error("Could not start channel", ex);
            }
        } else {
            logger.warn("Channel already started");
        }
    }

    @Override
    public synchronized void close() {
        logger.info("Closing channel... port={}", port);
        this.running.set(false);
        try {
            if (Objects.nonNull(selector)) {
                selector.wakeup();
            }
            sessionAttachments.values()
                              .stream()
                              .map(SessionAttachment::session)
                              .toList()
                              .forEach(this::closeSession);
            if (Objects.nonNull(selector)) {
                selector.close();
                selector = null;
            }
            if (Objects.nonNull(channel)) {
                channel.close();
                channel = null;
            }
            threadPool.shutdown();
            threadPool.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted while closing channel", ex);
        } catch (IOException ex) {
            logger.error("Error closing channel", ex);
        }
        logger.info("Channel closed port={}", port);
    }

    @Override
    public OutboundChannel outboundChannel() {
        return outboundChannel;
    }

    private void accept() {
        try {
            Objects.requireNonNull(selector, "selector cannot be null!");
            while (running.get()) {
                selector.select();
                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

                while (keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();
                    if (!key.isValid()) {
                        keyIterator.remove();
                        continue;
                    }
                    if (key.isAcceptable()) {
                        acceptSession(key);
                    } else if (key.isReadable()) {
                        threadPool.submit(() -> readSession(key));
                    }
                    keyIterator.remove();
                }
            }
        } catch (IOException ex) {
            logger.error("Accept loop error", ex);
        }
    }

    private void readSession(SelectionKey key) {
        var attachment = (SessionAttachment) key.attachment();
        if (Objects.nonNull(attachment.sslChannel())) {
            readSslSession(attachment, key);
            return;
        }
        var buffer = bufferPool.request();
        try {
            int length = attachment.socket().read(buffer);
            if (length < 0) {
                closeSession(attachment.session());
                key.cancel();
                return;
            }
            if (length > 0) {
                attachment.session().offer(buffer.array(), length);
            }
        } catch (IOException ex) {
            logger.debug("Read error, closing session", ex);
            closeSession(attachment.session());
            key.cancel();
        } finally {
            bufferPool.release(buffer);
        }
    }

    private void readSslSession(SessionAttachment attachment, SelectionKey key) {
        try {
            var read = attachment.sslChannel()
                                 .read(data -> attachment.session().offer(data, data.length));
            if (read < 0) {
                closeSession(attachment.session());
                key.cancel();
            }
        } catch (IOException ex) {
            logger.debug("SSL read error, closing session", ex);
            closeSession(attachment.session());
            key.cancel();
        }
    }

    private void acceptSession(SelectionKey key) {
        try {
            ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
            SocketChannel clientChannel = serverSocketChannel.accept();

            SslEngineChannel sslChannel = null;
            if (runtime.sslSettings().isPresent()) {
                clientChannel.configureBlocking(true);
                sslChannel = SslEngineChannel.wrap(clientChannel, runtime.sslSettings().get().sslContext());
            }
            clientChannel.configureBlocking(false);

            var outbound = new TcpSessionOutboundChannel(clientChannel, sslChannel);
            var session = new Session(outbound,
                                      listener,
                                      runtime.sessionConfig(),
                                      sessionCloser,
                                      runtime.heartbeatExecutor());

            var attachment = new SessionAttachment(session, clientChannel, sslChannel);
            sessionAttachments.put(session, attachment);
            clientChannel.register(selector, SelectionKey.OP_READ, attachment);

            logger.info("Session started, waiting for CONNECT: {}", session);
        } catch (IOException ex) {
            logger.error("Error accepting session", ex);
        }
    }

    private void closeSession(Session session) {
        var attachment = sessionAttachments.remove(session);
        if (Objects.isNull(attachment)) {
            return;
        }
        if (Objects.nonNull(attachment.sslChannel())) {
            attachment.sslChannel().close();
        } else {
            try {
                attachment.socket().close();
            } catch (IOException ex) {
                logger.debug("Error closing socket", ex);
            }
        }
        if (session.status() != Status.END) {
            listener.sessionDisconnected(session);
        }
    }

    @Override
    public String toString() {
        return "TcpChannel[port=%d]".formatted(port);
    }
}
