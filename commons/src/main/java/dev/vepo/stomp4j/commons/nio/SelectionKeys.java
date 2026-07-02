package dev.vepo.stomp4j.commons.nio;

import java.nio.channels.SelectionKey;

/**
 * <p>
 * <b>Responsibilities</b>
 * </p>
 * <ul>
 * <li><b>Doing:</b> Adjust non-blocking socket {@link SelectionKey} interest
 * ops for outbound draining.</li>
 * </ul>
 */
public final class SelectionKeys {

    public static void clearWriteInterest(SelectionKey key) {
        var interestOps = key.interestOps() & ~SelectionKey.OP_WRITE;
        if (interestOps == 0) {
            interestOps = SelectionKey.OP_READ;
        }
        key.interestOps(interestOps);
    }

    private SelectionKeys() {}
}
