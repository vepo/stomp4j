package io.vepo.stomp4j.protocol.transport;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vepo.stomp4j.protocol.MessageBuffer;
import io.vepo.stomp4j.protocol.StompListener;
import io.vepo.stomp4j.protocol.Transport;

public class TcpTransport implements Transport {
    private static final Logger logger = LoggerFactory.getLogger(TcpTransport.class);
    private final String host;
    private final int port;
    private final StompListener listener;
    private final ExecutorService executor;
    private Socket socket;
    private OutputStream os;
    private volatile long lastReceivedMessaged;
    private final AtomicLong idGenerator;

    public TcpTransport(URI uri, StompListener listener) {
        this.host = uri.getHost();
        this.port = uri.getPort();
        this.listener = listener;
        this.executor = Executors.newSingleThreadExecutor();
        this.lastReceivedMessaged = System.nanoTime();
        this.idGenerator = new AtomicLong();
    }

    public String host() {
        return host;
    };

    @Override
    public void send(String message) {
        try {
            os.write(message.getBytes());
            os.write("\n".getBytes());
            os.flush();
            logger.info("Sent message: {}", message);
        } catch (Exception e) {
            logger.error("Error sending message", e);
        }
    }

    public void close() {
        if (Objects.nonNull(socket)) {
            try {
                socket.close();
            } catch (IOException e) {
            }
        }
        executor.shutdown();
    };

    private void readMessages() {
        this.lastReceivedMessaged = System.nanoTime();
        try {
            var messageBuffer = new MessageBuffer();
            var is = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            var buffer = new char[1024];
            int length;
            while ((length = is.read(buffer)) != -1) {
                this.lastReceivedMessaged = System.nanoTime();
                if (messageBuffer.append(new String(buffer, 0, length))) {
                    do {
                        logger.info("Message complete. Sending to listener. listener: {}", listener);
                        listener.message(messageBuffer.message());
                        logger.info("Message sent to listener.");
                    } while (messageBuffer.hasMessage());
                }
            }
        } catch (IOException e) {
            logger.error("Error reading messages", e);
        } catch (Exception e) {
            logger.error("Error reading messages", e);
        }
        logger.info("Message reader thread finished.");
    }

    @Override
    public void connect() {
        logger.info("Trying to connect to {}:{}", host, port);
        try {
            this.socket = new Socket(host, port);
            this.os = socket.getOutputStream();
            executor.submit(this::readMessages);
            listener.connected(this);
        } catch (UnknownHostException uhe) {
            throw new RuntimeException(uhe);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public long silentTime() {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - lastReceivedMessaged);
    }

    public long nextId() {
        return idGenerator.incrementAndGet();
    }
}
