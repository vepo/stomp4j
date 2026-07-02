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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.vepo.stomp4j.client.transport.Transport;
import dev.vepo.stomp4j.client.transport.TransportListener;
import dev.vepo.stomp4j.commons.nio.SelectionKeys;
import dev.vepo.stomp4j.commons.nio.TcpOutboundQueue;
import dev.vepo.stomp4j.commons.protocol.Message;
import dev.vepo.stomp4j.commons.protocol.MessageBuffer;

/**
 * <p>
 * <b>Responsibilities</b>
 * </p>
 * <ul>
 * <li><b>Doing:</b> Own the shared non-blocking TCP selector loop, outbound
 * queue serialisation, lifecycle, and {@link Transport} timing for client NIO
 * transports; delegate plain vs TLS read/write to subclasses.</li>
 * </ul>
 * <p>
 * <b>Collaborators:</b> {@link TcpOutboundQueue}, {@link TransportListener},
 * {@link MessageBuffer}
 * </p>
 * <p>
 * <b>Not responsible for:</b> TLS handshake details, STOMP protocol
 * negotiation, heart-beat scheduling.
 * </p>
 */
abstract class AbstractNioTcpTransport implements Transport {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    protected final String host;
    protected final int port;
    protected final TransportListener listener;
    protected final TcpOutboundQueue outbound = new TcpOutboundQueue();
    protected final MessageBuffer messageBuffer = new MessageBuffer();
    protected final AtomicBoolean running = new AtomicBoolean(false);
    protected final CountDownLatch done = new CountDownLatch(1);
    protected final Object sendLock = new Object();

    protected SocketChannel socketChannel;
    protected Selector selector;
    protected SelectionKey selectionKey;
    protected Thread ioThread;
    protected volatile long lastReceivedMessage = System.nanoTime();
    protected volatile long lastSentMessage = System.nanoTime();

    protected AbstractNioTcpTransport(URI uri, TransportListener listener) {
        this.host = uri.getHost();
        this.port = uri.getPort();
        this.listener = listener;
    }

    private void abortConnect() {
        running.set(false);
        wakeupSelector();
        joinIoThread();
        awaitDone();
        closeOpenResources();
    }

    private void awaitDone() {
        try {
            done.await(1, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            logger.error("Interrupted while waiting for I/O thread to finish", ex);
        }
    }

    protected void beforeCloseResources() {
        // Plain TCP has no extra release step.
    }

    @Override
    public void close() {
        running.set(false);
        wakeupSelector();
        joinIoThread();
        awaitDone();
        closeOpenResources();
    }

    private void closeOpenResources() {
        synchronized (sendLock) {
            beforeCloseResources();
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
    }

    @Override
    public void connect() {
        logger.info("Trying to connect to {}:{}", host, port);
        running.set(true);
        var connected = false;
        try {
            socketChannel = SocketChannel.open();
            socketChannel.connect(new InetSocketAddress(host, port));
            prepareConnectedChannel(socketChannel);
            socketChannel.configureBlocking(false);
            selector = Selector.open();
            selectionKey = socketChannel.register(selector, SelectionKey.OP_READ);
            ioThread = Thread.ofPlatform()
                             .daemon(true)
                             .name(ioThreadName())
                             .start(this::ioLoop);
            listener.onConnected(this);
            connected = true;
        } catch (UnknownHostException ex) {
            logger.error("TCP connect failed for {}:{}", host, port, ex);
            throw TransportFailures.connectFailed("%s:%d".formatted(host, port), ex);
        } catch (IOException ex) {
            logger.error("TCP connect failed for {}:{}", host, port, ex);
            throw TransportFailures.connectFailed("%s:%d".formatted(host, port), ex);
        } finally {
            if (!connected) {
                abortConnect();
            }
        }
    }

    protected void deliverApplicationData(byte[] data, int length) {
        lastReceivedMessage = System.nanoTime();
        if (messageBuffer.append(data, 0, length)) {
            do {
                listener.onMessage(messageBuffer.message());
            } while (messageBuffer.hasMessage());
        }
    }

    protected abstract boolean flushOutbound() throws IOException;

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
                                SelectionKeys.clearWriteInterest(key);
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
            logger.info("NIO TCP I/O thread finished.");
        }
    }

    protected abstract String ioThreadName();

    private void joinIoThread() {
        var thread = ioThread;
        ioThread = null;
        if (Objects.isNull(thread) || !thread.isAlive()) {
            return;
        }
        try {
            thread.join(TimeUnit.SECONDS.toMillis(1));
        } catch (InterruptedException ex) {
            logger.error("Interrupted while joining NIO TCP I/O thread", ex);
        }
    }

    protected void markSent() {
        lastSentMessage = System.nanoTime();
    }

    @Override
    public long outboundSilentTime() {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - lastSentMessage);
    }

    protected abstract void prepareConnectedChannel(SocketChannel channel) throws IOException;

    protected abstract void readInbound() throws IOException;

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
                        SelectionKeys.clearWriteInterest(key);
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

    protected void stopRunning() {
        running.set(false);
    }

    private void wakeupSelector() {
        var currentSelector = selector;
        if (Objects.nonNull(currentSelector) && currentSelector.isOpen()) {
            currentSelector.wakeup();
        }
    }
}
