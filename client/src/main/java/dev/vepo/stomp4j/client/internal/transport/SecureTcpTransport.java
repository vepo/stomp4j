package dev.vepo.stomp4j.client.internal.transport;

import java.io.IOException;
import java.net.Socket;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.vepo.stomp4j.client.transport.Transport;
import dev.vepo.stomp4j.client.transport.TransportListener;
import dev.vepo.stomp4j.commons.protocol.Message;
import dev.vepo.stomp4j.commons.protocol.MessageBuffer;

public class SecureTcpTransport implements Transport {
    private static final Logger logger = LoggerFactory.getLogger(SecureTcpTransport.class);

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
    private final ExecutorService executor;
    private Socket socket;
    private volatile long lastReceivedMessage;
    private final AtomicBoolean running;

    private final CountDownLatch done;

    public SecureTcpTransport(URI uri, TransportListener listener) {
        this(uri, listener, defaultSslContext());
    }

    public SecureTcpTransport(URI uri, TransportListener listener, SSLContext sslContext) {
        this.host = uri.getHost();
        this.port = uri.getPort();
        this.listener = listener;
        this.sslContext = sslContext;
        this.executor = Executors.newSingleThreadExecutor();
        this.lastReceivedMessage = System.nanoTime();
        this.running = new AtomicBoolean(true);
        this.done = new CountDownLatch(1);
    }

    @Override
    public void close() {
        running.set(false);
        try {
            done.await(1, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        if (Objects.nonNull(socket)) {
            try {
                socket.close();
            } catch (IOException ex) {
                logger.error("Error closing socket", ex);
            }
        }
        executor.shutdown();
    }

    @Override
    public void connect() {
        logger.info("Trying to connect securely to {}:{}", host, port);
        try {
            SSLSocketFactory factory = sslContext.getSocketFactory();
            this.socket = factory.createSocket(host, port);
            if (socket instanceof SSLSocket sslSocket) {
                sslSocket.startHandshake();
            }
            executor.submit(this::readMessages);
            listener.onConnected(this);
        } catch (UnknownHostException ex) {
            throw TransportFailures.connectFailed("%s:%d".formatted(host, port), ex);
        } catch (IOException ex) {
            throw TransportFailures.connectFailed("%s:%d".formatted(host, port), ex);
        }
    }

    @Override
    public String host() {
        return host;
    }

    private void readMessages() {
        lastReceivedMessage = System.nanoTime();
        try {
            var messageBuffer = new MessageBuffer();
            var inputStream = socket.getInputStream();
            var buffer = new byte[1024];
            int length;
            while (running.get() && (length = inputStream.read(buffer)) != -1) {
                if (length > 0) {
                    lastReceivedMessage = System.nanoTime();
                    if (messageBuffer.append(buffer, 0, length)) {
                        do {
                            listener.onMessage(messageBuffer.message());
                        } while (messageBuffer.hasMessage());
                    }
                }
            }
        } catch (IOException ex) {
            logger.error("Error reading messages", ex);
        } finally {
            done.countDown();
        }
    }

    @Override
    public void send(Message message) {
        logger.atDebug()
              .addArgument(() -> Message.formatted(message.encode()))
              .log("Sending message: {}");
        try {
            var os = socket.getOutputStream();
            os.write(message.encode().getBytes());
            os.flush();
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
        return "SecureTcpTransport[host=%s, port=%d]".formatted(host, port);
    }
}
