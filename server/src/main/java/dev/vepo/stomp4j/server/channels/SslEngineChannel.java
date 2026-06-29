package dev.vepo.stomp4j.server.channels;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Objects;
import java.util.function.Consumer;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class SslEngineChannel {
    private static final Logger logger = LoggerFactory.getLogger(SslEngineChannel.class);

    private final SocketChannel socketChannel;
    private final SSLEngine engine;
    private final ByteBuffer inboundBuffer;
    private final ByteBuffer outboundBuffer;
    private ByteBuffer unwrapBuffer;

    private SslEngineChannel(SocketChannel socketChannel, SSLEngine engine) {
        this.socketChannel = socketChannel;
        this.engine = engine;
        this.inboundBuffer = ByteBuffer.allocate(engine.getSession().getPacketBufferSize());
        this.outboundBuffer = ByteBuffer.allocate(engine.getSession().getPacketBufferSize());
        this.unwrapBuffer = ByteBuffer.allocate(engine.getSession().getApplicationBufferSize());
    }

    static SslEngineChannel wrap(SocketChannel socketChannel, SSLContext sslContext) throws IOException {
        var engine = sslContext.createSSLEngine();
        engine.setUseClientMode(false);
        engine.beginHandshake();
        var channel = new SslEngineChannel(socketChannel, engine);
        channel.runHandshake();
        return channel;
    }

    private void runHandshake() throws IOException {
        var handshakeBuffer = ByteBuffer.allocate(engine.getSession().getApplicationBufferSize());
        var status = engine.getHandshakeStatus();
        while (status != SSLEngineResult.HandshakeStatus.FINISHED
                && status != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
            switch (status) {
                case NEED_UNWRAP, NEED_UNWRAP_AGAIN -> {
                    if (inboundBuffer.position() == inboundBuffer.limit()) {
                        inboundBuffer.clear();
                        var read = socketChannel.read(inboundBuffer);
                        if (read < 0) {
                            throw new IOException("SSL handshake failed: connection closed");
                        }
                        inboundBuffer.flip();
                    }
                    handshakeBuffer.clear();
                    var unwrapResult = engine.unwrap(inboundBuffer, handshakeBuffer);
                    if (unwrapResult.getStatus() == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
                        inboundBuffer.compact();
                        continue;
                    }
                    if (inboundBuffer.hasRemaining()) {
                        inboundBuffer.compact();
                    } else {
                        inboundBuffer.clear();
                    }
                    status = unwrapResult.getHandshakeStatus();
                }
                case NEED_WRAP -> {
                    outboundBuffer.clear();
                    var wrapResult = engine.wrap(ByteBuffer.allocate(0), outboundBuffer);
                    outboundBuffer.flip();
                    socketChannel.write(outboundBuffer);
                    status = wrapResult.getHandshakeStatus();
                }
                case NEED_TASK -> {
                    Runnable task;
                    while (Objects.nonNull(task = engine.getDelegatedTask())) {
                        task.run();
                    }
                    status = engine.getHandshakeStatus();
                }
                default -> status = engine.getHandshakeStatus();
            }
        }
    }

    int read(Consumer<byte[]> consumer) throws IOException {
        var read = socketChannel.read(inboundBuffer);
        if (read < 0) {
            return -1;
        }
        if (read == 0 && inboundBuffer.position() == inboundBuffer.limit()) {
            return 0;
        }
        inboundBuffer.flip();
        while (inboundBuffer.hasRemaining()) {
            unwrapBuffer.clear();
            var result = engine.unwrap(inboundBuffer, unwrapBuffer);
            if (result.getStatus() == SSLEngineResult.Status.BUFFER_OVERFLOW) {
                unwrapBuffer = ByteBuffer.allocate(unwrapBuffer.capacity() * 2);
                continue;
            }
            if (unwrapBuffer.position() > 0) {
                unwrapBuffer.flip();
                var data = new byte[unwrapBuffer.remaining()];
                unwrapBuffer.get(data);
                consumer.accept(data);
            }
            if (result.getStatus() == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
                break;
            }
        }
        inboundBuffer.compact();
        return read;
    }

    void write(byte[] data) throws IOException {
        var source = ByteBuffer.wrap(data);
        while (source.hasRemaining()) {
            outboundBuffer.clear();
            engine.wrap(source, outboundBuffer);
            outboundBuffer.flip();
            while (outboundBuffer.hasRemaining()) {
                socketChannel.write(outboundBuffer);
            }
        }
    }

    void close() {
        try {
            engine.closeOutbound();
            socketChannel.close();
        } catch (Exception ex) {
            logger.debug("Error closing SSL channel", ex);
        }
    }
}
