package dev.vepo.stomp4j.client;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class SubscribeOptions {

    public static final class Builder {
        private AckMode ackMode = AckMode.CLIENT;
        private final Map<String, String> headers = new HashMap<>();

        private Builder() {}

        public Builder ackMode(AckMode ackMode) {
            this.ackMode = Objects.requireNonNull(ackMode);
            return this;
        }

        public Builder header(String name, String value) {
            Objects.requireNonNull(name);
            Objects.requireNonNull(value);
            headers.put(name, value);
            return this;
        }

        public SubscribeOptions build() {
            return new SubscribeOptions(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static SubscribeOptions defaults() {
        return builder().build();
    }

    private final AckMode ackMode;
    private final Map<String, String> headers;

    private SubscribeOptions(Builder builder) {
        this.ackMode = builder.ackMode;
        this.headers = Map.copyOf(builder.headers);
    }

    public AckMode ackMode() {
        return ackMode;
    }

    public Map<String, String> headers() {
        return headers;
    }
}
