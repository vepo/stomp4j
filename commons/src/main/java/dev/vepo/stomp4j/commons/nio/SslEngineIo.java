package dev.vepo.stomp4j.commons.nio;

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
 * <b>Collaborators:</b> {@link TcpOutboundQueue}
 * </p>
 * <p>
 * <b>Not responsible for:</b> {@link java.nio.channels.SelectionKey} interest
 * ops or connection lifecycle.
 * </p>
 */
public final class SslEngineIo {

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

    public static SslEngineIo client(SSLContext sslContext, String host, int port) {
        Objects.requireNonNull(sslContext, "sslContext");
        var engine = sslContext.createSSLEngine(host, port);
        engine.setUseClientMode(true);
        return create(engine);
    }

    private static SslEngineIo create(SSLEngine engine) {
        try {
            engine.beginHandshake();
        } catch (SSLException ex) {
            throw new IllegalStateException("Could not begin TLS handshake", ex);
        }
        var session = engine.getSession();
        return new SslEngineIo(engine,
                               NioBuffers.allocate(session.getPacketBufferSize()),
                               NioBuffers.allocate(session.getApplicationBufferSize()),
                               NioBuffers.allocate(session.getPacketBufferSize()));
    }

    public static SslEngineIo server(SSLContext sslContext) {
        Objects.requireNonNull(sslContext, "sslContext");
        var engine = sslContext.createSSLEngine();
        engine.setUseClientMode(false);
        return create(engine);
    }

    private final SSLEngine engine;
    private ByteBuffer peerNetData;
    private ByteBuffer peerAppData;
    private ByteBuffer myNetData;
    private final ByteBuffer emptyAppBuffer;
    private ByteBuffer pendingAppWrite;
    private boolean handshakeComplete;

    private SslEngineIo(SSLEngine engine, ByteBuffer peerNetData, ByteBuffer peerAppData, ByteBuffer myNetData) {
        this.engine = engine;
        this.peerNetData = peerNetData;
        this.peerAppData = peerAppData;
        this.myNetData = myNetData;
        this.emptyAppBuffer = ByteBuffer.allocate(0);
        NioBuffers.prepareForSocketRead(this.peerNetData);
    }

    private void compactPeerNetData() {
        if (peerNetData.hasRemaining()) {
            peerNetData.compact();
        } else {
            NioBuffers.prepareForSocketRead(peerNetData);
        }
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
            if (result.getStatus() == Status.BUFFER_OVERFLOW) {
                myNetData = NioBuffers.enlarge(myNetData, myNetData.capacity() * 2);
                continue;
            }
            handleTerminalStatus(result.getStatus());
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
        NioBuffers.prepareForSocketRead(peerNetData);
        peerNetData.clear();
        var read = socket.read(peerNetData);
        if (read < 0) {
            throw new SSLException("Peer closed connection");
        }
        if (read == 0) {
            NioBuffers.prepareForSocketRead(peerNetData);
            return false;
        }
        peerNetData.flip();
        return true;
    }

    private void handleTerminalStatus(Status status) throws SSLException {
        if (status == Status.CLOSED) {
            throw new SSLException("SSL engine closed");
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
            while (ensurePeerNetData(socket)) {
                while (peerNetData.hasRemaining()) {
                    peerAppData.clear();
                    var result = engine.unwrap(peerNetData, peerAppData);
                    if (result.getStatus() == Status.BUFFER_OVERFLOW) {
                        peerAppData = NioBuffers.enlarge(peerAppData, peerAppData.capacity() * 2);
                        continue;
                    }
                    switch (result.getStatus()) {
                        case OK -> {}
                        case BUFFER_UNDERFLOW -> {
                            compactPeerNetData();
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
                compactPeerNetData();
            }
        } catch (IOException ex) {
            handler.onClose();
        }
    }

    private void runDelegatedTasks() {
        Runnable task;
        while (Objects.nonNull(task = engine.getDelegatedTask())) {
            task.run();
        }
    }

    public void shutdown(SocketChannel socket) {
        if (!handshakeComplete) {
            return;
        }
        try {
            if (!engine.isOutboundDone()) {
                engine.closeOutbound();
                writeEnginePackets(socket);
            }
        } catch (IOException ex) {
            // Peer may already be gone during abrupt close.
        }
        try {
            if (!engine.isInboundDone()) {
                engine.closeInbound();
            }
        } catch (SSLException ex) {
            // closing inbound before peer's close_notify is expected on abrupt close.
        }
    }

    private void unwrapHandshake(SocketChannel socket) throws IOException {
        if (!ensurePeerNetData(socket)) {
            return;
        }
        peerAppData.clear();
        var result = engine.unwrap(peerNetData, peerAppData);
        if (result.getStatus() == Status.BUFFER_OVERFLOW) {
            peerAppData = NioBuffers.enlarge(peerAppData, peerAppData.capacity() * 2);
            return;
        }
        handleTerminalStatus(result.getStatus());
        compactPeerNetData();
        if (result.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
            runDelegatedTasks();
        }
    }

    private void wrapHandshake(SocketChannel socket) throws IOException {
        SSLEngineResult result;
        while (true) {
            myNetData.clear();
            result = engine.wrap(emptyAppBuffer, myNetData);
            if (result.getStatus() == Status.BUFFER_OVERFLOW) {
                myNetData = NioBuffers.enlarge(myNetData, myNetData.capacity() * 2);
                continue;
            }
            break;
        }
        handleTerminalStatus(result.getStatus());
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

    private void writeEnginePackets(SocketChannel socket) throws IOException {
        while (!engine.isOutboundDone()) {
            myNetData.clear();
            var result = engine.wrap(emptyAppBuffer, myNetData);
            if (result.getStatus() == Status.BUFFER_OVERFLOW) {
                myNetData = NioBuffers.enlarge(myNetData, myNetData.capacity() * 2);
                continue;
            }
            if (result.getStatus() == Status.CLOSED) {
                break;
            }
            myNetData.flip();
            while (myNetData.hasRemaining()) {
                socket.write(myNetData);
            }
            if (result.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
                runDelegatedTasks();
            }
        }
    }
}
