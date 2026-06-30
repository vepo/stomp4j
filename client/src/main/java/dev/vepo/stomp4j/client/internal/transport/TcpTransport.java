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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.vepo.stomp4j.client.transport.Transport;
import dev.vepo.stomp4j.client.transport.TransportListener;
import dev.vepo.stomp4j.commons.protocol.Message;
import dev.vepo.stomp4j.commons.protocol.MessageBuffer;

public class TcpTransport implements Transport {
    private static final Logger logger = LoggerFactory.getLogger(TcpTransport.class);
    private final String host;
    private final int port;
    private final TransportListener listener;
    private final ExecutorService executor;
    private Socket socket;
    private volatile long lastReceivedMessaged;
    private volatile long lastSentMessage;
    private final AtomicBoolean running;
    private final CountDownLatch done;
    private final Object sendLock = new Object();

    public TcpTransport(URI uri, TransportListener listener) {
        this.host = uri.getHost();
        this.port = uri.getPort();
        this.listener = listener;
        this.executor = Executors.newSingleThreadExecutor();
        this.lastReceivedMessaged = System.nanoTime();
        this.lastSentMessage = System.nanoTime();
        this.running = new AtomicBoolean(true);
        this.done = new CountDownLatch(1);
    }

    public void close() {
        running.set(false);
        try {
            done.await(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            logger.error("Error waiting for message reader thread to finish", e);
        }
        if (Objects.nonNull(socket)) {
            try {
                socket.close();
            } catch (IOException e) {
                logger.error("Error closing socket", e);
            }
        }
        executor.shutdown();
    };

    @Override
    public void connect() {
        logger.info("Trying to connect to {}:{}", host, port);
        try {
            this.socket = new Socket(host, port);
            executor.submit(this::readMessages);
            listener.onConnected(this);
        } catch (UnknownHostException uhe) {
            throw TransportFailures.connectFailed("%s:%d".formatted(host, port), uhe);
        } catch (IOException e) {
            throw TransportFailures.connectFailed("%s:%d".formatted(host, port), e);
        }
    }

    public String host() {
        return host;
    };

    private void readMessages() {
        this.lastReceivedMessaged = System.nanoTime();
        try {
            var messageBuffer = new MessageBuffer();
            var inputStream = socket.getInputStream();
            var buffer = new byte[1024];
            int length;
            while (running.get() && (length = inputStream.read(buffer)) != -1) {
                if (length > 0) {
                    this.lastReceivedMessaged = System.nanoTime();
                    if (messageBuffer.append(buffer, 0, length)) {
                        do {
                            logger.info("Message complete. Sending to listener. listener: {}", listener);
                            listener.onMessage(messageBuffer.message());
                            logger.info("Message sent to listener.");
                        } while (messageBuffer.hasMessage());
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Error reading messages", e);
        } finally {
            done.countDown();
        }
        logger.info("Message reader thread finished.");
    }

    @Override
    public void send(Message message) {
        logger.atDebug()
              .addArgument(() -> Message.formatted(message.encode()))
              .log("Sending message: {}");

        try {
            synchronized (sendLock) {
                var os = socket.getOutputStream();
                os.write(message.encode().getBytes());
                os.flush();
                lastSentMessage = System.nanoTime();
            }
        } catch (Exception e) {
            throw TransportFailures.sendFailed(e);
        }
    }

    @Override
    public long outboundSilentTime() {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - lastSentMessage);
    }

    @Override
    public long silentTime() {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - lastReceivedMessaged);
    }

    @Override
    public String toString() {
        return "TcpTransport[host=%s, port=%d]".formatted(host, port);
    }
}
