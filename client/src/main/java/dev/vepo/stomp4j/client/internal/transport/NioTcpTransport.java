package dev.vepo.stomp4j.client.internal.transport;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import dev.vepo.stomp4j.client.transport.TransportListener;
import dev.vepo.stomp4j.commons.nio.TcpOutboundQueue;

/**
 * <p>
 * <b>Responsibilities</b>
 * </p>
 * <ul>
 * <li><b>Doing:</b> Plain TCP read/write for a STOMP client session over a
 * non-blocking {@link SocketChannel}.</li>
 * </ul>
 * <p>
 * <b>Collaborators:</b> {@link AbstractNioTcpTransport},
 * {@link TcpOutboundQueue}, {@link TransportListener}
 * </p>
 * <p>
 * <b>Not responsible for:</b> Selector lifecycle, STOMP protocol negotiation,
 * heart-beat scheduling, TLS.
 * </p>
 */
public class NioTcpTransport extends AbstractNioTcpTransport {

    private final ByteBuffer readBuffer = ByteBuffer.allocate(1024);

    public NioTcpTransport(URI uri, TransportListener listener) {
        super(uri, listener);
    }

    @Override
    protected boolean flushOutbound() throws IOException {
        if (!outbound.hasPending()) {
            return false;
        }
        var pending = outbound.drain(socketChannel);
        if (!pending) {
            markSent();
        }
        return pending;
    }

    @Override
    protected String ioThreadName() {
        return "nio-tcp-%s:%d".formatted(host, port);
    }

    @Override
    protected void prepareConnectedChannel(SocketChannel channel) {
        // Plain TCP needs no extra setup before non-blocking mode.
    }

    @Override
    protected void readInbound() throws IOException {
        int length;
        do {
            readBuffer.clear();
            length = socketChannel.read(readBuffer);
            if (length < 0) {
                stopRunning();
                return;
            }
            if (length > 0) {
                readBuffer.flip();
                var data = new byte[length];
                readBuffer.get(data);
                deliverApplicationData(data, length);
            }
        } while (length > 0);
    }

    @Override
    public String toString() {
        return "NioTcpTransport[host=%s, port=%d]".formatted(host, port);
    }
}
