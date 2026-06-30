package dev.vepo.stomp4j.server.tests.infra;

import java.time.Duration;

import static org.awaitility.Awaitility.await;

/**
 * Shared Awaitility patterns for embedded server tests.
 */
public final class ServerTestSupport {

    private ServerTestSupport() {
    }

    public static void settleFor(Duration delay) {
        await().atMost(delay.plusSeconds(1))
               .pollDelay(delay)
               .until(() -> true);
    }

    public static void awaitTrue(Duration timeout, java.util.function.BooleanSupplier condition) {
        await().atMost(timeout).until(condition::getAsBoolean);
    }
}
