package dev.vepo.stomp4j.server.channels;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Objects;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;
import javax.net.ssl.SSLException;

/**
 * <p>
 * <b>Responsibilities</b>
 * </p>
 * <ul>
 * <li><b>Doing:</b> Run a non-blocking {@link SSLEngine} handshake and
 * translate encrypted socket I/O to application plaintext for one TCP
 * session.</li>
 * </ul>
 * <p>
 * <b>Collaborators:</b> {@link TcpChannel}, {@link TcpOutboundQueue}
 * </p>
 * <p>
 * <b>Not responsible for:</b> {@link java.nio.channels.Selector} interest ops
 * or session lifecycle.
 * </p>
 */
public final class TcpSslIo {

    @FunctionalInterface
    public interface ApplicationDataHandler {

        default void onClose() {}

        void onData(byte[] data, int length);
    }

    public enum HandshakeProgress {
        CONTINUE,
        COMPLETE,
        FAILED
    }

    private static ByteBuffer allocateBuffer(int size) {
        return ByteBuffer.allocate(size);
    }

    private static void prepareForSocketRead(ByteBuffer buffer) {
        buffer.clear();
        buffer.limit(0);
    }

    public static TcpSslIo server(SSLContext sslContext) {
        Objects.requireNonNull(sslContext, "sslContext");
        var engine = sslContext.createSSLEngine();
        engine.setUseClientMode(false);
        try {
            engine.beginHandshake();
        } catch (SSLException ex) {
            throw new IllegalStateException("Could not begin TLS handshake", ex);
        }
        var session = engine.getSession();
        return new TcpSslIo(engine,
                            allocateBuffer(session.getPacketBufferSize()),
                            allocateBuffer(session.getApplicationBufferSize()),
                            allocateBuffer(session.getPacketBufferSize()));
    }

    private final SSLEngine engine;
    private final ByteBuffer peerNetData;
    private final ByteBuffer peerAppData;
    private final ByteBuffer myNetData;
    private final ByteBuffer emptyAppBuffer;
    private ByteBuffer pendingAppWrite;
    private boolean handshakeComplete;

    private TcpSslIo(SSLEngine engine, ByteBuffer peerNetData, ByteBuffer peerAppData, ByteBuffer myNetData) {
        this.engine = engine;
        this.peerNetData = peerNetData;
        this.peerAppData = peerAppData;
        this.myNetData = myNetData;
        this.emptyAppBuffer = ByteBuffer.allocate(0);
        prepareForSocketRead(this.peerNetData);
    }

    public boolean drainOutbound(SocketChannel socket, TcpOutboundQueue outbound) throws IOException {
        while (true) {
            if (myNetData.hasRemaining()) {
                socket.write(myNetData);
                if (myNetData.hasRemaining()) {
                    return true;
                }
            }
            if (Objects.isNull(pendingAppWrite)) {
                if (!outbound.hasPending()) {
                    return false;
                }
                var frame = outbound.pollFrame();
                if (Objects.isNull(frame)) {
                    return false;
                }
                pendingAppWrite = ByteBuffer.wrap(frame);
            }
            myNetData.clear();
            var result = engine.wrap(pendingAppWrite, myNetData);
            handleEngineStatus(result.getStatus());
            myNetData.flip();
            if (!pendingAppWrite.hasRemaining()) {
                pendingAppWrite = null;
            }
            if (result.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
                runDelegatedTasks();
            }
        }
    }

    private boolean ensurePeerNetData(SocketChannel socket) throws IOException {
        if (peerNetData.position() > 0 && peerNetData.limit() == peerNetData.capacity()) {
            peerNetData.flip();
        }
        if (peerNetData.hasRemaining()) {
            return true;
        }
        prepareForSocketRead(peerNetData);
        peerNetData.clear();
        var read = socket.read(peerNetData);
        if (read < 0) {
            throw new SSLException("Peer closed connection");
        }
        if (read == 0) {
            prepareForSocketRead(peerNetData);
            return false;
        }
        peerNetData.flip();
        return true;
    }

    private void handleEngineStatus(Status status) throws SSLException {
        if (status == Status.CLOSED) {
            throw new SSLException("SSL engine closed");
        }
        if (status == Status.BUFFER_OVERFLOW) {
            throw new SSLException("SSL buffer overflow");
        }
    }

    public HandshakeProgress handshake(SocketChannel socket) throws IOException {
        if (handshakeComplete) {
            return HandshakeProgress.COMPLETE;
        }
        try {
            var status = engine.getHandshakeStatus();
            if (status == HandshakeStatus.NOT_HANDSHAKING || status == HandshakeStatus.FINISHED) {
                handshakeComplete = true;
                return HandshakeProgress.COMPLETE;
            }
            switch (status) {
                case NEED_UNWRAP -> unwrapHandshake(socket);
                case NEED_WRAP -> wrapHandshake(socket);
                case NEED_TASK -> runDelegatedTasks();
                default -> {
                    return HandshakeProgress.CONTINUE;
                }
            }
            status = engine.getHandshakeStatus();
            if (status == HandshakeStatus.NOT_HANDSHAKING || status == HandshakeStatus.FINISHED) {
                handshakeComplete = true;
                return HandshakeProgress.COMPLETE;
            }
            return HandshakeProgress.CONTINUE;
        } catch (SSLException ex) {
            return HandshakeProgress.FAILED;
        }
    }

    public boolean hasEncryptedOutbound() {
        return myNetData.hasRemaining() || Objects.nonNull(pendingAppWrite);
    }

    public boolean isHandshakeComplete() {
        return handshakeComplete;
    }

    public void readApplication(SocketChannel socket, ApplicationDataHandler handler) throws IOException {
        try {
            if (!ensurePeerNetData(socket)) {
                return;
            }
            while (peerNetData.hasRemaining()) {
                peerAppData.clear();
                var result = engine.unwrap(peerNetData, peerAppData);
                switch (result.getStatus()) {
                    case OK, BUFFER_OVERFLOW -> {}
                    case BUFFER_UNDERFLOW -> {
                        return;
                    }
                    case CLOSED -> {
                        handler.onClose();
                        return;
                    }
                    default -> throw new SSLException("Unexpected unwrap status: %s".formatted(result.getStatus()));
                }
                if (result.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
                    runDelegatedTasks();
                }
                peerAppData.flip();
                if (peerAppData.hasRemaining()) {
                    var data = new byte[peerAppData.remaining()];
                    peerAppData.get(data);
                    handler.onData(data, data.length);
                }
            }
        } catch (SSLException ex) {
            handler.onClose();
        } finally {
            if (peerNetData.hasRemaining()) {
                peerNetData.compact();
            } else {
                prepareForSocketRead(peerNetData);
            }
        }
    }

    private void runDelegatedTasks() {
        Runnable task;
        while (Objects.nonNull(task = engine.getDelegatedTask())) {
            task.run();
        }
    }

    private void unwrapHandshake(SocketChannel socket) throws IOException {
        if (!ensurePeerNetData(socket)) {
            return;
        }
        peerAppData.clear();
        var result = engine.unwrap(peerNetData, peerAppData);
        handleEngineStatus(result.getStatus());
        if (peerNetData.hasRemaining()) {
            peerNetData.compact();
        } else {
            prepareForSocketRead(peerNetData);
        }
        if (result.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
            runDelegatedTasks();
        }
    }

    private void wrapHandshake(SocketChannel socket) throws IOException {
        myNetData.clear();
        var result = engine.wrap(emptyAppBuffer, myNetData);
        handleEngineStatus(result.getStatus());
        myNetData.flip();
        while (myNetData.hasRemaining()) {
            var written = socket.write(myNetData);
            if (written == 0) {
                return;
            }
        }
        if (result.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
            runDelegatedTasks();
        }
    }
}
