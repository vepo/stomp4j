package dev.vepo.stomp4j.client;

import java.time.Duration;
import java.util.Objects;

public final class SendOptions {

    public static final class Builder {
        private String contentType = "text/plain";
        private boolean receipt;
        private Duration receiptTimeout = DEFAULT_RECEIPT_TIMEOUT;

        private Builder() {}

        public SendOptions build() {
            return new SendOptions(this);
        }

        public Builder contentType(String contentType) {
            this.contentType = Objects.requireNonNull(contentType);
            return this;
        }

        public Builder receipt(boolean receipt) {
            this.receipt = receipt;
            return this;
        }

        public Builder receiptTimeout(Duration receiptTimeout) {
            this.receiptTimeout = Objects.requireNonNull(receiptTimeout);
            return this;
        }
    }

    public static final Duration DEFAULT_RECEIPT_TIMEOUT = Duration.ofSeconds(30);

    public static Builder builder() {
        return new Builder();
    }

    public static SendOptions plainText() {
        return builder().build();
    }

    private final String contentType;

    private final boolean receipt;

    private final Duration receiptTimeout;

    private SendOptions(Builder builder) {
        this.contentType = builder.contentType;
        this.receipt = builder.receipt;
        this.receiptTimeout = builder.receiptTimeout;
    }

    public String contentType() {
        return contentType;
    }

    public boolean receipt() {
        return receipt;
    }

    public Duration receiptTimeout() {
        return receiptTimeout;
    }
}
