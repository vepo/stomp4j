package dev.vepo.stomp4j.server.channels;

import java.io.IOException;
import java.io.OutputStream;
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

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.vepo.stomp4j.commons.protocol.Message;
import dev.vepo.stomp4j.server.AcknowledgedOutboundChannel;
import dev.vepo.stomp4j.server.OutboundChannel;
import dev.vepo.stomp4j.server.SubscriberAckListener;
import dev.vepo.stomp4j.server.session.Session;
import dev.vepo.stomp4j.server.session.SessionCloser;
import dev.vepo.stomp4j.server.session.Status;

public class TcpChannel implements Channel {
    private record SessionAttachment(Session session, SocketChannel socket) {}

    private class StreamOutboundChannel implements OutboundChannel {

        private final OutputStream outputStream;

        private StreamOutboundChannel(OutputStream outputStream) {
            this.outputStream = outputStream;
        }

        @Override
        public void send(Message message) {
            try {
                outputStream.write(message.encode().getBytes());
                outputStream.flush();
            } catch (IOException ex) {
                logger.error("Error sending message: %s".formatted(message), ex);
            }
        }
    }

    private class TcpExternalOutboundChannel implements AcknowledgedOutboundChannel {

        @Override
        public void send(Message message) {
            send(message, null);
        }

        @Override
        public void send(Message message, SubscriberAckListener listener) {
            logger.debug("Sending message to all active sessions: count={}", sessionAttachments.size());
            var ackListener = java.util.Optional.ofNullable(listener);
            sessionAttachments.values()
                              .stream()
                              .map(SessionAttachment::session)
                              .forEach(session -> session.handle(message, ackListener));
        }
    }

    private class TcpSessionOutboundChannel implements OutboundChannel {

        private final SocketChannel socket;

        private TcpSessionOutboundChannel(SocketChannel socket) {
            this.socket = socket;
        }

        @Override
        public void send(Message message) {
            try {
                socket.write(ByteBuffer.wrap(message.encode().getBytes()));
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
    private final Map<Session, SSLSocket> sslSessions;
    private final TcpExternalOutboundChannel outboundChannel;
    private final SessionCloser sessionCloser;
    private Selector selector;
    private ServerSocketChannel channel;
    private SSLServerSocket sslServerSocket;

    public TcpChannel(int port, ChannelListener listener, ChannelRuntime runtime) {
        this.port = port;
        this.listener = listener;
        this.runtime = runtime;
        this.threadPool = Executors.newFixedThreadPool(10);
        this.running = new AtomicBoolean(false);
        this.bufferPool = new BufferPool(10, 1024);
        this.sessionAttachments = new ConcurrentHashMap<>();
        this.sslSessions = new ConcurrentHashMap<>();
        this.outboundChannel = new TcpExternalOutboundChannel();
        this.sessionCloser = this::closeSession;
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
                        readSession(key);
                    }
                    keyIterator.remove();
                }
            }
        } catch (IOException ex) {
            logger.error("Accept loop error", ex);
        }
    }

    private void acceptSession(SelectionKey key) {
        SocketChannel clientChannel = null;
        Session session = null;
        var sessionRegistered = false;
        try {
            var serverSocketChannel = (ServerSocketChannel) key.channel();
            clientChannel = serverSocketChannel.accept();
            clientChannel.configureBlocking(false);

            var outbound = new TcpSessionOutboundChannel(clientChannel);
            session = new Session(outbound,
                                  listener,
                                  runtime.sessionConfig(),
                                  sessionCloser,
                                  runtime.heartbeatExecutor());

            var attachment = new SessionAttachment(session, clientChannel);
            sessionAttachments.put(session, attachment);
            clientChannel.register(selector, SelectionKey.OP_READ, attachment);
            sessionRegistered = true;

            logger.info("Session started, waiting for CONNECT: {}", session);
        } catch (Exception ex) {
            logger.error("Error accepting session", ex);
        } finally {
            if (!sessionRegistered) {
                if (Objects.nonNull(session)) {
                    sessionAttachments.remove(session);
                }
                if (Objects.nonNull(clientChannel)) {
                    try {
                        clientChannel.close();
                    } catch (IOException closeEx) {
                        logger.debug("Error closing accepted socket after setup failure", closeEx);
                    }
                }
            }
        }
    }

    private void acceptSsl() {
        while (running.get()) {
            SSLSocket sslSocket = null;
            var handedOffToReader = false;
            try {
                sslSocket = (SSLSocket) sslServerSocket.accept();
                sslSocket.startHandshake();
                var readerSocket = sslSocket;
                threadPool.submit(() -> readSslSocket(readerSocket));
                handedOffToReader = true;
            } catch (Exception ex) {
                if (running.get()) {
                    logger.error("SSL accept error", ex);
                }
            } finally {
                if (!handedOffToReader && Objects.nonNull(sslSocket)) {
                    try {
                        sslSocket.close();
                    } catch (IOException closeEx) {
                        logger.debug("Error closing SSL socket after handshake failure", closeEx);
                    }
                }
            }
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
            sslSessions.values().forEach(socket -> {
                try {
                    socket.close();
                } catch (IOException ex) {
                    logger.debug("Error closing SSL socket", ex);
                }
            });
            sslSessions.clear();
            if (Objects.nonNull(sslServerSocket)) {
                sslServerSocket.close();
                sslServerSocket = null;
            }
            if (Objects.nonNull(selector)) {
                selector.close();
                selector = null;
            }
            if (Objects.nonNull(channel)) {
                channel.close();
                channel = null;
            }
            shutdownThreadPool();
        } catch (IOException ex) {
            logger.error("Error closing channel", ex);
        }
        logger.info("Channel closed port={}", port);
    }

    private void closeSession(Session session) {
        var attachment = sessionAttachments.remove(session);
        var sslSocket = sslSessions.remove(session);
        if (Objects.nonNull(attachment)) {
            try {
                attachment.socket().close();
            } catch (IOException ex) {
                logger.debug("Error closing socket", ex);
            }
        }
        if (Objects.nonNull(sslSocket)) {
            try {
                sslSocket.close();
            } catch (IOException ex) {
                logger.debug("Error closing SSL socket", ex);
            }
        }
        if (session.status() != Status.END) {
            listener.sessionDisconnected(session);
        }
    }

    @Override
    public OutboundChannel outboundChannel() {
        return outboundChannel;
    }

    private void readSession(SelectionKey key) {
        var attachment = (SessionAttachment) key.attachment();
        try {
            int length;
            do {
                var buffer = bufferPool.request();
                try {
                    length = attachment.socket().read(buffer);
                    if (length < 0) {
                        closeSession(attachment.session());
                        key.cancel();
                        return;
                    }
                    if (length > 0) {
                        buffer.flip();
                        var data = new byte[length];
                        buffer.get(data, 0, length);
                        attachment.session().offer(data, length);
                    }
                } finally {
                    bufferPool.release(buffer);
                }
            } while (length > 0);
        } catch (IOException ex) {
            logger.debug("Read error, closing session", ex);
            closeSession(attachment.session());
            key.cancel();
        }
    }

    private void readSslSocket(SSLSocket sslSocket) {
        Session session = null;
        try {
            var outbound = new StreamOutboundChannel(sslSocket.getOutputStream());
            session = new Session(outbound,
                                  listener,
                                  runtime.sessionConfig(),
                                  sessionCloser,
                                  runtime.heartbeatExecutor());
            sslSessions.put(session, sslSocket);
            logger.info("SSL session started, waiting for CONNECT: {}", session);
            var inputStream = sslSocket.getInputStream();
            var buffer = new byte[1024];
            int length;
            while (running.get() && (length = inputStream.read(buffer)) != -1) {
                if (length > 0) {
                    session.offer(buffer, length);
                }
            }
        } catch (IOException ex) {
            logger.debug("SSL session read error", ex);
        } finally {
            if (Objects.nonNull(session)) {
                closeSession(session);
            }
            try {
                sslSocket.close();
            } catch (IOException ex) {
                logger.debug("Error closing SSL socket", ex);
            }
        }
    }

    private void rollbackStartup() {
        running.set(false);
        if (Objects.nonNull(sslServerSocket)) {
            try {
                sslServerSocket.close();
            } catch (IOException ex) {
                logger.debug("Error closing SSL server socket during startup rollback", ex);
            }
            sslServerSocket = null;
        }
        if (Objects.nonNull(selector)) {
            try {
                selector.close();
            } catch (IOException ex) {
                logger.debug("Error closing selector during startup rollback", ex);
            }
            selector = null;
        }
        if (Objects.nonNull(channel)) {
            try {
                channel.close();
            } catch (IOException ex) {
                logger.debug("Error closing server channel during startup rollback", ex);
            }
            channel = null;
        }
        shutdownThreadPool();
    }

    private void shutdownThreadPool() {
        if (threadPool.isShutdown()) {
            return;
        }
        threadPool.shutdown();
        try {
            threadPool.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted while shutting down channel thread pool", ex);
        }
    }

    @Override
    public synchronized void start() {
        if (running.get()) {
            logger.warn("Channel already started");
            return;
        }
        logger.info("Starting TCP Channel at port {}", port);
        try {
            if (runtime.sslSettings().isPresent()) {
                sslServerSocket = (SSLServerSocket) runtime.sslSettings()
                                                           .get()
                                                           .sslContext()
                                                           .getServerSocketFactory()
                                                           .createServerSocket(port);
                running.set(true);
                threadPool.submit(this::acceptSsl);
            } else {
                this.selector = Selector.open();
                this.channel = ServerSocketChannel.open();
                channel.configureBlocking(false);
                channel.bind(new InetSocketAddress(port));
                channel.register(selector, SelectionKey.OP_ACCEPT);
                running.set(true);
                threadPool.submit(this::accept);
            }
            logger.info("TCP Channel started! port={}", port);
        } catch (Exception ex) {
            logger.error("Could not start channel on port {}", port, ex);
            rollbackStartup();
            throw new IllegalStateException("Could not start TCP channel on port %d".formatted(port), ex);
        }
    }

    @Override
    public String toString() {
        return "TcpChannel[port=%d]".formatted(port);
    }
}
