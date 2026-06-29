package dev.vepo.stomp4j.spring.boot.autoconfigure.server;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import dev.vepo.stomp4j.commons.TransportType;

@ConfigurationProperties(prefix = "stomp4j.server")
public class StompServerProperties {

    public static class Channel {
        private TransportType type = TransportType.TCP;
        private int port = 5500;

        public int getPort() {
            return port;
        }

        public TransportType getType() {
            return type;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public void setType(TransportType type) {
            this.type = type;
        }
    }

    private boolean enabled = true;
    private String serverName = "stomp4j";
    private Duration heartbeat = Duration.ofSeconds(30);

    private List<Channel> channels = new ArrayList<>();

    public List<Channel> getChannels() {
        return channels;
    }

    public Duration getHeartbeat() {
        return heartbeat;
    }

    public String getServerName() {
        return serverName;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setChannels(List<Channel> channels) {
        this.channels = channels;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setHeartbeat(Duration heartbeat) {
        this.heartbeat = heartbeat;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }
}
