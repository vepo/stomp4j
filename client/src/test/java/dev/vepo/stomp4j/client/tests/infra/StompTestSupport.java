package dev.vepo.stomp4j.client.tests.infra;

import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.function.IntSupplier;

/**
 * Shared Awaitility patterns for broker-backed client integration tests.
 * Call {@link #settleSubscription()} after SUBSCRIBE and before SEND to avoid races with Artemis.
 */
public final class StompTestSupport {

    public static final Duration SUBSCRIPTION_SETTLE_DELAY = Duration.ofMillis(250);
    public static final Duration MESSAGE_COLLECTION_TIMEOUT = Duration.ofSeconds(30);

    private StompTestSupport() {
    }

    public static void settleSubscription() {
        await().pollDelay(SUBSCRIPTION_SETTLE_DELAY).until(() -> true);
    }

    public static void awaitMessageCount(int expected, IntSupplier actualSize) {
        await().atMost(MESSAGE_COLLECTION_TIMEOUT)
               .pollInterval(Duration.ofMillis(50))
               .until(() -> actualSize.getAsInt() == expected);
    }

}
