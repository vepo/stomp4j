module stomp4j.kafka.bridge {
    requires transitive stomp4j.server;
    requires kafka.clients;
    requires org.slf4j;

    exports dev.vepo.stomp4j.bridge;

    opens dev.vepo.stomp4j.bridge to stomp4j.kafka.bridge.test;
}
