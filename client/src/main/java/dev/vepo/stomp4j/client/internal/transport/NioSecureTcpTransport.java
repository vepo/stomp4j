package dev.vepo.stomp4j.client.internal.transport;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.vepo.stomp4j.client.transport.Transport;
import dev.vepo.stomp4j.client.transport.TransportListener;
import dev.vepo.stomp4j.commons.protocol.Message;
import dev.vepo.stomp4j.commons.protocol.MessageBuffer;

/**
 * <p>
 * <b>Responsibilities</b>
 * </p>
 * <ul>
 * <li><b>Doing:</b> Connect a TLS STOMP session with non-blocking
 * {@link SocketChannel} I/O and {@link NioTcpSslIo} on a dedicated selector
 * thread; deliver inbound frames to {@link TransportListener} and send outbound
 * frames from any caller thread. Concurrent {@link #send} calls are serialised
 * on an internal lock; inbound data is read only on the selector I/O
 * thread.</li>
 * </ul>
 * <p>
 * <b>Collaborators:</b> {@link NioTcpSslIo}, {@link NioTcpOutboundQueue},
 * {@link TransportListener}, {@link MessageBuffer}
 * </p>
 * <p>
 * <b>Not responsible for:</b> STOMP protocol negotiation or heart-beat
 * scheduling.
 * </p>
 */
public class NioSecureTcpTransport implements Transport {

    private static final Logger logger = LoggerFactory.getLogger(NioSecureTcpTransport.class);

    private static SSLContext defaultSslContext() {
        try {
            return SSLContext.getDefault();
        } catch (Exception ex) {
            throw new IllegalStateException("Could not load default SSL context", ex);
        }
    }

    private final String host;
    private final int port;
    private final TransportListener listener;
    private final SSLContext sslContext;
    private final NioTcpOutboundQueue outbound = new NioTcpOutboundQueue();
    private final MessageBuffer messageBuffer = new MessageBuffer();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final CountDownLatch done = new CountDownLatch(1);
    private final Object sendLock = new Object();

    private SocketChannel socketChannel;
    private Selector selector;
    private SelectionKey selectionKey;
    private NioTcpSslIo sslIo;
    private Thread ioThread;
    private volatile long lastReceivedMessage = System.nanoTime();
    private volatile long lastSentMessage = System.nanoTime();

    public NioSecureTcpTransport(URI uri, TransportListener listener) {
        this(uri, listener, defaultSslContext());
    }

    public NioSecureTcpTransport(URI uri, TransportListener listener, SSLContext sslContext) {
        this.host = uri.getHost();
        this.port = uri.getPort();
        this.listener = listener;
        this.sslContext = sslContext;
    }

    private void abortConnect() {
        running.set(false);
        var currentSelector = selector;
        if (Objects.nonNull(currentSelector) && currentSelector.isOpen()) {
            currentSelector.wakeup();
        }
        joinIoThread();
        try {
            done.await(1, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            logger.error("Interrupted while waiting for I/O thread after connect failure", ex);
        }
        closeOpenResources();
    }

    private void clearWriteInterest(SelectionKey key) {
        var interestOps = key.interestOps() & ~SelectionKey.OP_WRITE;
        if (interestOps == 0) {
            interestOps = SelectionKey.OP_READ;
        }
        key.interestOps(interestOps);
    }

    @Override
    public void close() {
        running.set(false);
        var currentSelector = selector;
        if (Objects.nonNull(currentSelector) && currentSelector.isOpen()) {
            currentSelector.wakeup();
        }
        joinIoThread();
        try {
            done.await(1, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            logger.error("Error waiting for I/O thread to finish", ex);
        }
        closeOpenResources();
    }

    private void closeOpenResources() {
        synchronized (sendLock) {
            if (Objects.nonNull(sslIo) && Objects.nonNull(socketChannel)) {
                sslIo.shutdown(socketChannel);
            }
        }
        if (Objects.nonNull(socketChannel)) {
            try {
                socketChannel.close();
            } catch (IOException ex) {
                logger.debug("Error closing socket channel", ex);
            }
            socketChannel = null;
        }
        if (Objects.nonNull(selector)) {
            try {
                if (selector.isOpen()) {
                    selector.close();
                }
            } catch (IOException ex) {
                logger.debug("Error closing selector", ex);
            }
            selector = null;
        }
        selectionKey = null;
        sslIo = null;
    }

    private void completeBlockingHandshake() throws IOException {
        socketChannel.configureBlocking(true);
        try {
            while (!sslIo.isHandshakeComplete()) {
                var progress = sslIo.handshake(socketChannel);
                if (progress == NioTcpSslIo.HandshakeProgress.FAILED) {
                    throw new SSLException("TLS handshake failed");
                }
            }
        } finally {
            socketChannel.configureBlocking(false);
        }
    }

    @Override
    public void connect() {
        logger.info("Trying to connect securely to {}:{}", host, port);
        running.set(true);
        var connected = false;
        try {
            socketChannel = SocketChannel.open();
            socketChannel.connect(new InetSocketAddress(host, port));
            sslIo = NioTcpSslIo.client(sslContext, host, port);
            completeBlockingHandshake();
            selector = Selector.open();
            selectionKey = socketChannel.register(selector, SelectionKey.OP_READ);
            ioThread = Thread.ofPlatform()
                             .daemon(true)
                             .name("nio-tcp-ssl-%s:%d".formatted(host, port))
                             .start(this::ioLoop);
            listener.onConnected(this);
            connected = true;
        } catch (UnknownHostException ex) {
            logger.error("Secure TCP connect failed for {}:{}", host, port, ex);
            throw TransportFailures.connectFailed("%s:%d".formatted(host, port), ex);
        } catch (IOException ex) {
            logger.error("Secure TCP connect failed for {}:{}", host, port, ex);
            throw TransportFailures.connectFailed("%s:%d".formatted(host, port), ex);
        } finally {
            if (!connected) {
                abortConnect();
            }
        }
    }

    private boolean flushOutbound() throws IOException {
        if (!hasOutboundPending()) {
            return false;
        }
        var pending = sslIo.drainOutbound(socketChannel, outbound);
        if (!pending) {
            lastSentMessage = System.nanoTime();
        }
        return pending;
    }

    private boolean hasOutboundPending() {
        return outbound.hasPending() || sslIo.hasEncryptedOutbound();
    }

    @Override
    public String host() {
        return host;
    }

    private void ioLoop() {
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
                    var key = keyIterator.next();
                    keyIterator.remove();
                    if (!key.isValid()) {
                        continue;
                    }
                    if (key.isReadable()) {
                        readInbound();
                    }
                    if (key.isValid() && key.isWritable()) {
                        synchronized (sendLock) {
                            if (!flushOutbound()) {
                                clearWriteInterest(key);
                            }
                        }
                    }
                }
            }
        } catch (IOException ex) {
            if (running.get()) {
                logger.error("I/O loop error", ex);
            } else {
                logger.debug("I/O loop ended during shutdown", ex);
            }
        } finally {
            done.countDown();
            logger.info("NIO secure TCP I/O thread finished.");
        }
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
            logger.error("Interrupted while joining NIO secure TCP I/O thread", ex);
        }
    }

    @Override
    public long outboundSilentTime() {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - lastSentMessage);
    }

    private void readInbound() throws IOException {
        sslIo.readApplication(socketChannel, new NioTcpSslIo.ApplicationDataHandler() {
            @Override
            public void onClose() {
                running.set(false);
            }

            @Override
            public void onData(byte[] data, int length) {
                lastReceivedMessage = System.nanoTime();
                if (messageBuffer.append(data, 0, length)) {
                    do {
                        listener.onMessage(messageBuffer.message());
                    } while (messageBuffer.hasMessage());
                }
            }
        });
    }

    @Override
    public void send(Message message) {
        logger.atDebug()
              .addArgument(() -> Message.formatted(message.encode()))
              .log("Sending message: {}");

        try {
            synchronized (sendLock) {
                if (Objects.isNull(socketChannel) || !socketChannel.isOpen()) {
                    throw TransportFailures.notConnected();
                }
                outbound.enqueue(message.encode().getBytes());
                var key = selectionKey;
                var currentSelector = selector;
                if (Objects.nonNull(key) && key.isValid()) {
                    if (!flushOutbound()) {
                        clearWriteInterest(key);
                    } else {
                        key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
                    }
                    if (Objects.nonNull(currentSelector) && currentSelector.isOpen()) {
                        currentSelector.wakeup();
                    }
                }
            }
        } catch (Exception ex) {
            throw TransportFailures.sendFailed(ex);
        }
    }

    @Override
    public long silentTime() {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - lastReceivedMessage);
    }

    @Override
    public String toString() {
        return "NioSecureTcpTransport[host=%s, port=%d]".formatted(host, port);
    }
}
