package dev.vepo.stomp4j.spring.boot.autoconfigure.client;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import dev.vepo.stomp4j.client.AckMode;
import dev.vepo.stomp4j.commons.TransportType;

@ConfigurationProperties(prefix = "stomp4j.client")
public class StompClientProperties {

    private boolean enabled = true;
    private String url = "stomp://localhost:61613";
    private String username;
    private String password;
    private TransportType transportType;
    private AckMode defaultAckMode = AckMode.CLIENT_INDIVIDUAL;
    private Duration receiptTimeout = Duration.ofSeconds(30);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public TransportType getTransportType() {
        return transportType;
    }

    public void setTransportType(TransportType transportType) {
        this.transportType = transportType;
    }

    public AckMode getDefaultAckMode() {
        return defaultAckMode;
    }

    public void setDefaultAckMode(AckMode defaultAckMode) {
        this.defaultAckMode = defaultAckMode;
    }

    public Duration getReceiptTimeout() {
        return receiptTimeout;
    }

    public void setReceiptTimeout(Duration receiptTimeout) {
        this.receiptTimeout = receiptTimeout;
    }
}
