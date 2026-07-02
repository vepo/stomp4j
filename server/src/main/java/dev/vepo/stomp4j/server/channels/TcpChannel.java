package dev.vepo.stomp4j.server.channels;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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

    private static final class SessionAttachment {

        private Session session;
        private final SocketChannel socket;
        private final TcpSslIo sslIo;
        private final TcpOutboundQueue outbound = new TcpOutboundQueue();
        private final Object ioLock = new Object();
        private SelectionKey selectionKey;

        private SessionAttachment(SocketChannel socket) {
            this(socket, null);
        }

        private SessionAttachment(SocketChannel socket, TcpSslIo sslIo) {
            this.socket = socket;
            this.sslIo = sslIo;
        }

        private void bind(Session session) {
            this.session = session;
        }

        private boolean handshakePending() {
            return Objects.nonNull(sslIo) && !sslIo.isHandshakeComplete();
        }

        private Object ioLock() {
            return ioLock;
        }

        private TcpOutboundQueue outbound() {
            return outbound;
        }

        private SelectionKey selectionKey() {
            return selectionKey;
        }

        private void selectionKey(SelectionKey selectionKey) {
            this.selectionKey = selectionKey;
        }

        private Session session() {
            return session;
        }

        private SocketChannel socket() {
            return socket;
        }

        private TcpSslIo sslIo() {
            return sslIo;
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

        private final SessionAttachment attachment;

        private TcpSessionOutboundChannel(SessionAttachment attachment) {
            this.attachment = attachment;
        }

        @Override
        public void send(Message message) {
            enqueueOutbound(attachment, message.encode().getBytes());
        }
    }

    private static final Logger logger = LoggerFactory.getLogger(TcpChannel.class);

    private final int port;
    private final ChannelListener listener;
    private final ChannelRuntime runtime;
    private final AtomicBoolean running;
    private final BufferPool bufferPool;
    private final Map<Session, SessionAttachment> sessionAttachments;
    private final TcpExternalOutboundChannel outboundChannel;
    private final SessionCloser sessionCloser;
    private Thread ioThread;
    private Selector selector;
    private ServerSocketChannel channel;

    public TcpChannel(int port, ChannelListener listener, ChannelRuntime runtime) {
        this.port = port;
        this.listener = listener;
        this.runtime = runtime;
        this.running = new AtomicBoolean(false);
        this.bufferPool = new BufferPool(10, 1024);
        this.sessionAttachments = new ConcurrentHashMap<>();
        this.outboundChannel = new TcpExternalOutboundChannel();
        this.sessionCloser = this::closeSession;
    }

    private void accept() {
        try {
            while (running.get()) {
                var currentSelector = selector;
                if (Objects.isNull(currentSelector) || !currentSelector.isOpen()) {
                    return;
                }
                int ready;
                try {
                    ready = currentSelector.select();
                } catch (ClosedSelectorException ex) {
                    return;
                }
                if (!running.get()) {
                    return;
                }
                if (ready == 0) {
                    continue;
                }
                var selectedKeys = currentSelector.selectedKeys();
                var keyIterator = selectedKeys.iterator();

                while (keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();
                    if (!key.isValid()) {
                        keyIterator.remove();
                        continue;
                    }
                    if (key.isAcceptable()) {
                        acceptSession(key);
                    }
                    if (key.isValid() && key.isReadable()) {
                        handleReadable(key);
                    }
                    if (key.isValid() && key.isWritable()) {
                        handleWritable(key);
                    }
                    keyIterator.remove();
                }
            }
        } catch (IOException ex) {
            if (running.get()) {
                logger.error("Accept loop error", ex);
            } else {
                logger.debug("Accept loop ended during shutdown", ex);
            }
        }
    }

    private Session acceptPlainSession(SocketChannel clientChannel) throws Exception {
        var attachment = new SessionAttachment(clientChannel);
        var outbound = new TcpSessionOutboundChannel(attachment);
        var session = new Session(outbound,
                                  listener,
                                  runtime.sessionConfig(),
                                  sessionCloser,
                                  runtime.heartbeatExecutor());
        attachment.bind(session);
        attachment.selectionKey(clientChannel.register(selector, SelectionKey.OP_READ, attachment));
        sessionAttachments.put(session, attachment);
        logger.info("Session started, waiting for CONNECT: {}", session);
        return session;
    }

    private void acceptSession(SelectionKey key) {
        SocketChannel clientChannel = null;
        Session session = null;
        var sessionRegistered = false;
        var tlsHandshakeStarted = false;
        try {
            var serverSocketChannel = (ServerSocketChannel) key.channel();
            clientChannel = serverSocketChannel.accept();
            clientChannel.configureBlocking(false);

            if (runtime.sslSettings().isPresent()) {
                acceptTlsSession(clientChannel);
                tlsHandshakeStarted = true;
            } else {
                session = acceptPlainSession(clientChannel);
                sessionRegistered = true;
            }
        } catch (Exception ex) {
            logger.error("Error accepting session", ex);
        } finally {
            if (!sessionRegistered && !tlsHandshakeStarted) {
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

    private void acceptTlsSession(SocketChannel clientChannel) throws IOException {
        var sslIo = TcpSslIo.server(runtime.sslSettings().get().sslContext());
        var attachment = new SessionAttachment(clientChannel, sslIo);
        var selectionKey = clientChannel.register(selector, SelectionKey.OP_READ, attachment);
        attachment.selectionKey(selectionKey);
        progressTlsHandshake(attachment, selectionKey);
    }

    private void clearWriteInterest(SelectionKey key) {
        var interestOps = key.interestOps() & ~SelectionKey.OP_WRITE;
        if (interestOps == 0) {
            interestOps = SelectionKey.OP_READ;
        }
        key.interestOps(interestOps);
    }

    @Override
    public synchronized void close() {
        logger.info("Closing channel... port={}", port);
        this.running.set(false);
        try {
            var currentSelector = selector;
            if (Objects.nonNull(currentSelector) && currentSelector.isOpen()) {
                currentSelector.wakeup();
            }
            joinIoThread();
            if (Objects.nonNull(currentSelector) && currentSelector.isOpen()) {
                for (var key : currentSelector.keys()) {
                    if (key.attachment() instanceof SessionAttachment attachment) {
                        synchronized (attachment.ioLock()) {
                            shutdownTlsIo(attachment);
                            try {
                                attachment.socket().close();
                            } catch (IOException ex) {
                                logger.debug("Error closing socket during channel shutdown", ex);
                            }
                        }
                    }
                }
            }
            sessionAttachments.values()
                              .stream()
                              .map(SessionAttachment::session)
                              .filter(Objects::nonNull)
                              .toList()
                              .forEach(this::closeSession);
            if (Objects.nonNull(selector) && selector.isOpen()) {
                selector.close();
            }
            selector = null;
            if (Objects.nonNull(channel)) {
                channel.close();
                channel = null;
            }
        } catch (IOException ex) {
            logger.error("Error closing channel", ex);
        }
        logger.info("Channel closed port={}", port);
    }

    private void closePendingTlsAttachment(SessionAttachment attachment, SelectionKey key) {
        shutdownTlsIo(attachment);
        try {
            attachment.socket().close();
        } catch (IOException ex) {
            logger.debug("Error closing socket after TLS handshake failure", ex);
        }
        key.cancel();
    }

    private void closeSession(Session session) {
        var attachment = sessionAttachments.remove(session);
        if (Objects.nonNull(attachment)) {
            synchronized (attachment.ioLock()) {
                shutdownTlsIo(attachment);
                try {
                    attachment.socket().close();
                } catch (IOException ex) {
                    logger.debug("Error closing socket", ex);
                }
            }
        }
        if (session.status() != Status.END) {
            listener.sessionDisconnected(session);
        }
    }

    private void completeTlsHandshake(SessionAttachment attachment, SelectionKey key) throws Exception {
        var outbound = new TcpSessionOutboundChannel(attachment);
        var session = new Session(outbound,
                                  listener,
                                  runtime.sessionConfig(),
                                  sessionCloser,
                                  runtime.heartbeatExecutor());
        attachment.bind(session);
        sessionAttachments.put(session, attachment);
        key.interestOps(SelectionKey.OP_READ);
        logger.info("SSL session started, waiting for CONNECT: {}", session);
    }

    private void enqueueOutbound(SessionAttachment attachment, byte[] frame) {
        synchronized (attachment.ioLock()) {
            attachment.outbound().enqueue(frame);
            var key = attachment.selectionKey();
            if (Objects.nonNull(key) && key.isValid()) {
                key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
                flushOutbound(attachment, key);
                if (hasOutboundPending(attachment) && Objects.nonNull(selector)) {
                    selector.wakeup();
                }
            }
        }
    }

    private void flushOutbound(SessionAttachment attachment, SelectionKey key) {
        try {
            if (!hasOutboundPending(attachment)) {
                clearWriteInterest(key);
                return;
            }
            boolean pending;
            if (Objects.nonNull(attachment.sslIo())) {
                pending = attachment.sslIo().drainOutbound(attachment.socket(), attachment.outbound());
            } else {
                pending = attachment.outbound().drain(attachment.socket());
            }
            if (!pending) {
                clearWriteInterest(key);
            }
        } catch (IOException ex) {
            logger.debug("Write error, closing session", ex);
            if (Objects.nonNull(attachment.session())) {
                closeSession(attachment.session());
            } else {
                closePendingTlsAttachment(attachment, key);
            }
            key.cancel();
        }
    }

    private void handleReadable(SelectionKey key) {
        var attachment = (SessionAttachment) key.attachment();
        if (attachment.handshakePending()) {
            progressTlsHandshake(attachment, key);
            return;
        }
        readSession(key);
    }

    private void handleWritable(SelectionKey key) {
        var attachment = (SessionAttachment) key.attachment();
        if (attachment.handshakePending()) {
            progressTlsHandshake(attachment, key);
            return;
        }
        writeSession(key);
    }

    private boolean hasOutboundPending(SessionAttachment attachment) {
        return attachment.outbound().hasPending()
                || (Objects.nonNull(attachment.sslIo()) && attachment.sslIo().hasEncryptedOutbound());
    }

    private void joinIoThread() {
        var thread = ioThread;
        ioThread = null;
        if (Objects.isNull(thread) || !thread.isAlive()) {
            return;
        }
        try {
            thread.join(TimeUnit.SECONDS.toMillis(1));
        } catch (InterruptedException ex) {
            logger.error("Interrupted while joining TCP channel I/O thread", ex);
        }
    }

    @Override
    public OutboundChannel outboundChannel() {
        return outboundChannel;
    }

    private void progressTlsHandshake(SessionAttachment attachment, SelectionKey key) {
        var readAfterHandshake = false;
        synchronized (attachment.ioLock()) {
            try {
                var progress = attachment.sslIo().handshake(attachment.socket());
                switch (progress) {
                    case COMPLETE -> {
                        completeTlsHandshake(attachment, key);
                        readAfterHandshake = true;
                    }
                    case FAILED -> closePendingTlsAttachment(attachment, key);
                    case CONTINUE -> key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                    default -> {}
                }
            } catch (Exception ex) {
                logger.debug("TLS handshake error", ex);
                closePendingTlsAttachment(attachment, key);
            }
        }
        if (readAfterHandshake) {
            readSession(key);
        }
    }

    private void readSession(SelectionKey key) {
        var attachment = (SessionAttachment) key.attachment();
        synchronized (attachment.ioLock()) {
            try {
                if (Objects.nonNull(attachment.sslIo())) {
                    attachment.sslIo().readApplication(attachment.socket(), new TcpSslIo.ApplicationDataHandler() {
                        @Override
                        public void onClose() {
                            closeSession(attachment.session());
                            key.cancel();
                        }

                        @Override
                        public void onData(byte[] data, int length) {
                            attachment.session().offer(data, length);
                        }
                    });
                    return;
                }
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
    }

    private void rollbackStartup() {
        running.set(false);
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
    }

    private void shutdownTlsIo(SessionAttachment attachment) {
        if (Objects.isNull(attachment.sslIo())) {
            return;
        }
        attachment.sslIo().shutdown(attachment.socket());
    }

    @Override
    public synchronized void start() {
        if (running.get()) {
            logger.warn("Channel already started");
            return;
        }
        logger.info("Starting TCP Channel at port {}", port);
        try {
            this.selector = Selector.open();
            this.channel = ServerSocketChannel.open();
            channel.configureBlocking(false);
            channel.bind(new InetSocketAddress(port));
            channel.register(selector, SelectionKey.OP_ACCEPT);
            running.set(true);
            ioThread = Thread.ofPlatform()
                             .daemon(true)
                             .name("tcp-channel-%d".formatted(port))
                             .start(this::accept);
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

    private void writeSession(SelectionKey key) {
        var attachment = (SessionAttachment) key.attachment();
        synchronized (attachment.ioLock()) {
            flushOutbound(attachment, key);
        }
    }
}
