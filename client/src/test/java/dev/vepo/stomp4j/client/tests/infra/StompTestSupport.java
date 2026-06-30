package dev.vepo.stomp4j.client.tests.infra;

import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.IntSupplier;

/**
 * Shared Awaitility patterns for broker-backed client integration tests. Call
 * {@link #settleSubscription()} after SUBSCRIBE and before SEND to avoid races
 * with Artemis.
 */
public final class StompTestSupport {

    public static final Duration SUBSCRIPTION_SETTLE_DELAY = Duration.ofMillis(250);
    public static final Duration MESSAGE_COLLECTION_TIMEOUT = Duration.ofSeconds(30);

    public static void awaitMessageCount(int expected, IntSupplier actualSize) {
        await().atMost(MESSAGE_COLLECTION_TIMEOUT)
               .pollInterval(Duration.ofMillis(50))
               .until(() -> actualSize.getAsInt() == expected);
    }

    public static void settleFor(Duration delay) {
        await().atMost(delay.plusSeconds(1))
               .pollDelay(delay)
               .until(() -> true);
    }

    public static void settleSubscription() {
        settleFor(SUBSCRIPTION_SETTLE_DELAY);
    }

    /**
     * Message callbacks run on the transport reader thread; use this list with
     * {@code subscribe(topic, list::add)}.
     */
    public static List<String> threadSafeMessageList() {
        return Collections.synchronizedList(new ArrayList<>());
    }

    private StompTestSupport() {}
}
