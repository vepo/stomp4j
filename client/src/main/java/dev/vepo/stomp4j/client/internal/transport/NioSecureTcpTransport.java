package dev.vepo.stomp4j.client.internal.transport;

import java.io.IOException;
import java.net.URI;
import java.nio.channels.SocketChannel;
import java.util.Objects;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;

import dev.vepo.stomp4j.client.transport.TransportListener;
import dev.vepo.stomp4j.commons.nio.SslEngineIo;
import dev.vepo.stomp4j.commons.nio.TcpOutboundQueue;

/**
 * <p>
 * <b>Responsibilities</b>
 * </p>
 * <ul>
 * <li><b>Doing:</b> TLS read/write for a STOMP client session using
 * {@link SslEngineIo} over a non-blocking {@link SocketChannel}.</li>
 * </ul>
 * <p>
 * <b>Collaborators:</b> {@link AbstractNioTcpTransport}, {@link SslEngineIo},
 * {@link TcpOutboundQueue}, {@link TransportListener}
 * </p>
 * <p>
 * <b>Not responsible for:</b> Selector lifecycle, STOMP protocol negotiation,
 * heart-beat scheduling.
 * </p>
 */
public class NioSecureTcpTransport extends AbstractNioTcpTransport {

    private static SSLContext defaultSslContext() {
        try {
            return SSLContext.getDefault();
        } catch (Exception ex) {
            throw new IllegalStateException("Could not load default SSL context", ex);
        }
    }

    private final SSLContext sslContext;

    private SslEngineIo sslIo;

    public NioSecureTcpTransport(URI uri, TransportListener listener) {
        this(uri, listener, defaultSslContext());
    }

    public NioSecureTcpTransport(URI uri, TransportListener listener, SSLContext sslContext) {
        super(uri, listener);
        this.sslContext = sslContext;
    }

    @Override
    protected void beforeCloseResources() {
        if (Objects.nonNull(sslIo) && Objects.nonNull(socketChannel)) {
            sslIo.shutdown(socketChannel);
        }
        sslIo = null;
    }

    private void completeBlockingHandshake(SocketChannel channel) throws IOException {
        channel.configureBlocking(true);
        try {
            while (!sslIo.isHandshakeComplete()) {
                var progress = sslIo.handshake(channel);
                if (progress == SslEngineIo.HandshakeProgress.FAILED) {
                    throw new SSLException("TLS handshake failed");
                }
            }
        } finally {
            channel.configureBlocking(false);
        }
    }

    @Override
    protected boolean flushOutbound() throws IOException {
        if (!hasOutboundPending()) {
            return false;
        }
        var pending = sslIo.drainOutbound(socketChannel, outbound);
        if (!pending) {
            markSent();
        }
        return pending;
    }

    private boolean hasOutboundPending() {
        return outbound.hasPending() || sslIo.hasEncryptedOutbound();
    }

    @Override
    protected String ioThreadName() {
        return "nio-tcp-ssl-%s:%d".formatted(host, port);
    }

    @Override
    protected void prepareConnectedChannel(SocketChannel channel) throws IOException {
        sslIo = SslEngineIo.client(sslContext, host, port);
        completeBlockingHandshake(channel);
    }

    @Override
    protected void readInbound() throws IOException {
        sslIo.readApplication(socketChannel, new SslEngineIo.ApplicationDataHandler() {
            @Override
            public void onClose() {
                stopRunning();
            }

            @Override
            public void onData(byte[] data, int length) {
                deliverApplicationData(data, length);
            }
        });
    }

    @Override
    public String toString() {
        return "NioSecureTcpTransport[host=%s, port=%d]".formatted(host, port);
    }
}
