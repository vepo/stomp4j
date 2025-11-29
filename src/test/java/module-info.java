module stomp4j.test {
    requires transitive stomp4j;
    requires org.junit.jupiter.api;
    requires activemq.client;
    requires org.assertj.core;
    requires jakarta.messaging;
    requires org.slf4j;
    requires org.junit.jupiter.params;
    opens dev.vepo.stomp4j.tests to org.junit.platform.commons;
    opens dev.vepo.stomp4j.tests.infra to org.junit.platform.commons;
    opens dev.vepo.stomp4j.tests.protocol to org.junit.platform.commons;
}
