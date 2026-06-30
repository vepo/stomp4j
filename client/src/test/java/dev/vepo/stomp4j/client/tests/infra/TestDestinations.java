package dev.vepo.stomp4j.client.tests.infra;

import java.util.UUID;

/**
 * <p>
 * <b>Responsibilities</b>
 * </p>
 * <ul>
 * <li><b>Doing:</b> Generate exclusive STOMP/JMS destination names for
 * parallel-safe tests.</li>
 * </ul>
 * <p>
 * <b>Collaborators:</b> none
 * </p>
 * <p>
 * <b>Not responsible for:</b> broker connections, publishing, or assertions.
 * </p>
 */
public final class TestDestinations {

    public static String uniqueQueue(String prefix) {
        return "/queue/" + prefix + "-" + UUID.randomUUID();
    }

    public static String uniqueTopic() {
        return "/topic/" + UUID.randomUUID();
    }

    private TestDestinations() {}
}
