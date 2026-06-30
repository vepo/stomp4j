package dev.vepo.stomp4j.server.tests.infra;

import java.util.UUID;

/**
 * <p><b>Responsibilities</b></p>
 * <ul>
 *   <li><b>Doing:</b> Generate exclusive destination names for parallel-safe server tests.</li>
 * </ul>
 * <p><b>Collaborators:</b> none</p>
 * <p><b>Not responsible for:</b> server lifecycle or assertions.</p>
 */
public final class TestDestinations {

    private TestDestinations() {
    }

    public static String uniqueTopic() {
        return "/topic/" + UUID.randomUUID();
    }

    public static String uniqueTopic(String prefix) {
        return "/topic/" + prefix + "-" + UUID.randomUUID();
    }

    public static String uniqueQueue(String prefix) {
        return "/queue/" + prefix + "-" + UUID.randomUUID();
    }
}
