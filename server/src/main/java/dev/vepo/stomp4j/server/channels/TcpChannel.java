package dev.vepo.stomp4j.server.channels;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Collections;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.vepo.stomp4j.commons.protocol.Message;
import dev.vepo.stomp4j.server.OutboundChannel;
import dev.vepo.stomp4j.server.session.Session;

public class TcpChannel implements Channel {
    private class TcpExternalOutboundChannel implements OutboundChannel {

        @Override
        public void send(Message message) {
            logger.info("Message received! sending to all active sessions: activeSessions={} message={}", activeSessions, message);
            activeSessions.forEach(session -> session.handle(message));
        }

    }

    private class TcpSessionOutboundChannel implements OutboundChannel {

        private final SocketChannel channel;

        private TcpSessionOutboundChannel(SocketChannel channel) {
            this.channel = channel;
        }

        @Override
        public void send(Message message) {
            try {
                this.channel.write(ByteBuffer.wrap(message.encode().getBytes()));
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

    }

    private static final Logger logger = LoggerFactory.getLogger(TcpChannel.class);

    private final int port;
    private final ChannelListener listener;
    private final ExecutorService threadPool;
    private final AtomicBoolean running;
    private final BufferPool bufferPool;
    private final Set<Session> activeSessions;
    private final TcpExternalOutboundChannel outboundChannel;
    private Selector selector;

    public TcpChannel(int port, ChannelListener listener) {
        this.port = port;
        this.listener = listener;
        this.threadPool = Executors.newFixedThreadPool(10);
        this.running = new AtomicBoolean(false);
        this.bufferPool = new BufferPool(10, 1024);
        this.outboundChannel = new TcpExternalOutboundChannel();
        this.activeSessions = Collections.newSetFromMap(new WeakHashMap<>());
        this.selector = null;
    }

    @Override
    public void start() {
        logger.info("Starting TCP Channel at port {}", port);
        try {
            this.selector = Selector.open();
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.configureBlocking(false);
            serverSocketChannel.bind(new InetSocketAddress(port));
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

            this.running.set(true);
            this.threadPool.submit(this::accept);
        } catch (IOException e) {}
    }

    @Override
    public void close() {
        logger.info("Closing channel... port={}", port);
        this.running.set(false);
        try {
            selector.close();
            threadPool.close();
            threadPool.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            logger.error("Thread killed!!!", ex);
        } catch (IOException ex) {
            logger.error("Error closing channel!", ex);
        }
        logger.info("Channel closed!!! port={}", port);
    }

    @Override
    public OutboundChannel outboundChannel() {
        return this.outboundChannel;
    }

    private void accept() {
        try {
            Objects.requireNonNull(selector, "selector cannot be null!");
            while (this.running.get()) {
                selector.select();
                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

                while (keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();

                    if (key.isValid() && key.isAcceptable()) {
                        logger.info("Accepting new connection: {}", key);
                        acceptSession(key);
                    } else if (key.isValid() && key.isReadable()) {
                        logger.info("Reading from connection: {}", key);
                        threadPool.submit(() -> readSession(key));
                    }

                    keyIterator.remove();
                }
            }
        } catch (IOException e) {}
    }

    private void readSession(SelectionKey key) {
        SocketChannel client = (SocketChannel) key.channel();
        var session = (Session) key.attachment();
        var buffer = bufferPool.request();
        try {
            int length = client.read(buffer);
            if (length > 0) {
                logger.info("Read data: length: {}", length);
                var data = buffer.array();
                session.offer(data, length);
            }
        } catch (IOException e) {
            // nothing
        } finally {
            bufferPool.release(buffer);
        }
    }

    private void acceptSession(SelectionKey key) {
        try {
            ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
            SocketChannel clientChannel = serverSocketChannel.accept();
            clientChannel.configureBlocking(false);

            // Register for read operations with attachment for session data
            var session = new Session(new TcpSessionOutboundChannel(clientChannel), this.listener);
            this.activeSessions.add(session);
            clientChannel.register(selector, SelectionKey.OP_READ, session);

            logger.info("Session started! Waiting conenct message... {}", session);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String toString() {
        return "TcpChannel[port=%d]".formatted(port);
    }

}
