module stomp4j.integration {
    requires transitive stomp4j.client;
    requires transitive stomp4j.server;

    requires java.base;

    exports dev.vepo.stomp4j.integration.client;
    exports dev.vepo.stomp4j.integration.server;
}
