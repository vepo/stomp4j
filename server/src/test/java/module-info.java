module stomp4j.server.test {
    requires transitive stomp4j.server;
    requires transitive stomp4j.client;
    requires awaitility;
    requires org.junit.jupiter.api;
    requires activemq.client;
    requires org.assertj.core;
    requires jakarta.messaging;
    requires org.slf4j;
    requires org.junit.jupiter.params;

    opens dev.vepo.stomp4j.server.tests to org.junit.platform.commons;
}
