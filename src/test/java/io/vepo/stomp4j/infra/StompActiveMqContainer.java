package io.vepo.stomp4j.infra;

import java.time.Duration;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;

public class StompActiveMqContainer extends GenericContainer<StompActiveMqContainer> {

    private static final String DEFAULT_USER = "user";
    private static final String DEFAULT_PASSWORD = "passwd";
    private String username;
    private String password;

    public StompActiveMqContainer() {
        super(new ImageFromDockerfile("vepo/activemq-stomp", false).withDockerfileFromBuilder(builder -> builder.from("apache/activemq-artemis:2.30.0"))
                                                                   .withFileFromClasspath("/var/lib/artemis-instance/etc/broker.xml", "/broker.xml")
                                                                   .get());
        this.username = DEFAULT_USER;
        this.password = DEFAULT_PASSWORD;
    }

    public StompActiveMqContainer withUser(String username) {
        this.username = username;
        return this;
    }

    public StompActiveMqContainer withPassword(String password) {
        this.password = password;
        return this;
    }

    public String clientUrl() {
        return String.format("tcp://%s:%d", getHost(), getMappedPort(61616));
    }

    public String username() {
        return username;
    }

    public String password() {
        return password;
    }

    public String webSocketUrl() {
        return String.format("ws://%s:%d/stomp", getHost(), getMappedPort(61616));
    }

    public String tcpUrl() {
        return String.format("stomp://%s:%d", getHost(), getMappedPort(61613));
    }

    @Override
    protected void configure() {
        withEnv("ARTEMIS_USER", username);
        withEnv("ARTEMIS_PASSWORD", password);
        withExposedPorts(61613, 61614, 61616);
        waitingFor(Wait.forLogMessage(".*HTTP Server started.*", 1)
                       .withStartupTimeout(Duration.ofMinutes(2)));
    }
}
