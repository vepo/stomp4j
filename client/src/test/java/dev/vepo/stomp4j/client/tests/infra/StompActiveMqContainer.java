package dev.vepo.stomp4j.client.tests.infra;

import java.time.Duration;

import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

public class StompActiveMqContainer extends GenericContainer<StompActiveMqContainer> {

    private static final String DEFAULT_USER = "user";
    private static final String DEFAULT_PASSWORD = "passwd";
    private static final String BROKER_CONFIG_MOUNT = "/opt/stomp4j-broker.xml";
    private String username;
    private String password;

    public StompActiveMqContainer() {
        super("apache/activemq-artemis:2.30.0");
        this.username = DEFAULT_USER;
        this.password = DEFAULT_PASSWORD;
        withReuse(false);
    }

    public String clientUrl() {
        return String.format("tcp://%s:%d", getHost(), getMappedPort(61616));
    }

    @Override
    protected void configure() {
        withEnv("ARTEMIS_USER", username);
        withEnv("ARTEMIS_PASSWORD", password);
        withExposedPorts(61613, 61614, 61616);
        withClasspathResourceMapping("broker.xml", BROKER_CONFIG_MOUNT, BindMode.READ_ONLY);
        withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint("/bin/bash",
                                                                 "-ec",
                                                                 "if [ ! -x bin/artemis ]; then "
                                                                         + "/opt/activemq-artemis/bin/artemis create "
                                                                         + "--user \"${ARTEMIS_USER}\" --password \"${ARTEMIS_PASSWORD}\" "
                                                                         + "--require-login --silent --host 0.0.0.0 --http-host 0.0.0.0 --relax-jolokia .; "
                                                                         + "fi && cp "
                                                                         + BROKER_CONFIG_MOUNT
                                                                         + " etc/broker.xml && exec bin/artemis run")
                                                 .withWorkingDir("/var/lib/artemis-instance"));
        waitingFor(Wait.forLogMessage(".*HTTP Server started.*", 1)
                       .withStartupTimeout(Duration.ofMinutes(2)));
    }

    public String password() {
        return password;
    }

    public String tcpUrl() {
        return String.format("stomp://%s:%d", getHost(), getMappedPort(61613));
    }

    public String username() {
        return username;
    }

    public String webSocketUrl() {
        return String.format("ws://%s:%d", getHost(), getMappedPort(61614));
    }

    public StompActiveMqContainer withPassword(String password) {
        this.password = password;
        return this;
    }

    public StompActiveMqContainer withUser(String username) {
        this.username = username;
        return this;
    }
}
