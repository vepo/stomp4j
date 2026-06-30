package dev.vepo.stomp4j.server.tests.infra;

import java.io.IOException;
import java.net.ServerSocket;

/**
 * <p>
 * <b>Responsibilities</b>
 * </p>
 * <ul>
 * <li><b>Doing:</b> Allocate ephemeral TCP ports for parallel-safe embedded
 * server tests.</li>
 * </ul>
 * <p>
 * <b>Collaborators:</b> none
 * </p>
 * <p>
 * <b>Not responsible for:</b> starting servers or test assertions.
 * </p>
 */
public final class EphemeralPorts {

    public static int allocate() {
        try (var socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException ex) {
            throw new IllegalStateException("Could not allocate ephemeral port", ex);
        }
    }

    private EphemeralPorts() {}
}
